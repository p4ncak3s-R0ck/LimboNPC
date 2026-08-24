package dev.limbonpc.limbo.bridge;

import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.player.PluginMessageEvent;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.plugins.LimboPlugin;
import dev.limbonpc.common.protocol.ConnectRequest;
import dev.limbonpc.common.protocol.HealthPing;
import dev.limbonpc.common.protocol.HealthPong;
import dev.limbonpc.common.protocol.Protocol;
import dev.limbonpc.common.protocol.ProtocolCodec;
import dev.limbonpc.common.protocol.ProtocolMessage;
import dev.limbonpc.common.protocol.TransferResponse;
import dev.limbonpc.common.protocol.TransferResult;
import dev.limbonpc.limbo.config.LimboConfig;
import dev.limbonpc.limbo.npc.RuntimeNpc;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class VelocityBridgeClient implements Listener {
    private enum RequestKind { TRANSFER, HEALTH }
    private record Pending(Player player, RequestKind kind, long sentAt) {}

    private final LimboPlugin plugin;
    private final Supplier<LimboConfig> config;
    private final LimboMetrics metrics;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final AtomicLong requestIds = new AtomicLong(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private volatile String lastBackend = "unknown";

    public VelocityBridgeClient(LimboPlugin plugin, Supplier<LimboConfig> config, LimboMetrics metrics) {
        this.plugin = plugin; this.config = config; this.metrics = metrics;
    }

    public void requestServerTransfer(Player player, RuntimeNpc npc) {
        long id = nextId();
        send(player, new ConnectRequest(id, player.getUniqueId(), npc.definition().id(), npc.definition().server()), RequestKind.TRANSFER);
    }

    public void probe(Player player) {
        message(player, "status-probe", "<gray>Checking the Velocity bridge...");
        long id = nextId();
        send(player, new HealthPing(id, player.getUniqueId()), RequestKind.HEALTH);
    }

    @EventHandler public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getChannel().equals(config.get().channel())) return;
        ProtocolMessage decoded;
        try { decoded = ProtocolCodec.decodeMessage(event.getData()); }
        catch (ProtocolCodec.ProtocolException e) { metrics.error(); debug("Rejected proxy response: " + e.getMessage()); return; }
        if (decoded instanceof TransferResponse response) handleTransferResponse(response);
        else if (decoded instanceof HealthPong pong) handlePong(pong);
    }

    private void handleTransferResponse(TransferResponse response) {
        Pending request = pending.remove(response.requestId());
        if (request == null || request.kind() != RequestKind.TRANSFER) return;
        metrics.acknowledgement();
        debug("ACK " + response.requestId() + " result=" + response.result() + " detail=" + response.detail());
        switch (response.result()) {
            case DISPATCHED -> { }
            case UNKNOWN_SERVER -> message(request.player(), "unknown-server", "<red>That server is currently unavailable.");
            case RATE_LIMITED -> message(request.player(), "rate-limited", "<yellow>Please wait before selecting another server.");
            default -> message(request.player(), "transfer-failed", "<red>The server transfer could not be started.");
        }
    }

    private void handlePong(HealthPong pong) {
        Pending request = pending.remove(pong.requestId());
        if (request == null || request.kind() != RequestKind.HEALTH) return;
        metrics.acknowledgement(); lastBackend = pong.backend();
        String template = config.get().message("status-online", "<green>Velocity bridge online via '<backend>' (protocol <protocol>).");
        sendFormatted(request.player(), template.replace("<backend>", escape(pong.backend())).replace("<protocol>", String.valueOf(pong.protocolVersion())));
    }

    private void send(Player player, ProtocolMessage message, RequestKind kind) {
        pending.put(message.requestId(), new Pending(player, kind, System.currentTimeMillis()));
        metrics.request();
        try {
            player.sendPluginMessage(Key.key(config.get().channel()), ProtocolCodec.encode(message));
            debug("Sent " + message.getClass().getSimpleName() + " id=" + message.requestId() + " player=" + player.getName());
        } catch (IOException | RuntimeException e) {
            pending.remove(message.requestId()); metrics.error();
            message(player, "transfer-failed", "<red>The server transfer could not be started.");
            debug("Send failed: " + e.getMessage()); return;
        }
        long ticks = Math.max(1, (config.get().acknowledgementTimeoutMs() + 49) / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> timeout(message.requestId()), ticks);
    }

    private void timeout(long requestId) {
        Pending request = pending.remove(requestId);
        if (request == null) return;
        metrics.timeout(); debug("Request timed out id=" + requestId);
        message(request.player(), "bridge-timeout", "<red>The server selector bridge did not respond.");
    }

    public int pendingCount() { return pending.size(); }
    public String lastBackend() { return lastBackend; }
    public void clear() { pending.clear(); }

    private long nextId() { return requestIds.updateAndGet(value -> value == Long.MAX_VALUE ? 1 : value + 1); }
    private void message(Player player, String key, String fallback) { sendFormatted(player, config.get().message(key, fallback)); }
    private void sendFormatted(Player player, String value) { if (player.isValid()) player.sendMessage(mini.deserialize(config.get().prefix() + value)); }
    private void debug(String value) { if (config.get().debug()) plugin.getServer().getConsole().sendMessage("[LimboNPC/debug] " + value); }
    private static String escape(String value) { return value.replace("<", "\\<"); }
}
