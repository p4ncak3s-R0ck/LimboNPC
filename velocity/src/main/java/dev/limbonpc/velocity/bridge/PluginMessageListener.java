package dev.limbonpc.velocity.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import dev.limbonpc.common.protocol.ConnectRequest;
import dev.limbonpc.common.protocol.HealthPing;
import dev.limbonpc.common.protocol.HealthPong;
import dev.limbonpc.common.protocol.Protocol;
import dev.limbonpc.common.protocol.ProtocolCodec;
import dev.limbonpc.common.protocol.ProtocolMessage;
import dev.limbonpc.common.protocol.TransferResponse;
import dev.limbonpc.common.protocol.TransferResult;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;

public final class PluginMessageListener {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Supplier<VelocityConfig> config;
    private final Supplier<ChannelIdentifier> channel;
    private final VelocityMetrics metrics;
    private final RequestRateLimiter rateLimiter = new RequestRateLimiter();

    public PluginMessageListener(ProxyServer proxy, Logger logger, Supplier<VelocityConfig> config,
                                 Supplier<ChannelIdentifier> channel, VelocityMetrics metrics) {
        this.proxy = proxy; this.logger = logger; this.config = config; this.channel = channel; this.metrics = metrics;
    }

    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel.get())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        metrics.received();
        if (!(event.getSource() instanceof ServerConnection source)) {
            reject("client-originated plugin message"); return;
        }
        VelocityConfig currentConfig = config.get();
        String sourceName = source.getServerInfo().getName();
        if (!currentConfig.trusts(sourceName)) { reject("message from untrusted backend '" + sourceName + "'"); return; }

        ProtocolMessage message;
        try { message = ProtocolCodec.decodeMessage(event.getData()); }
        catch (ProtocolCodec.ProtocolException e) { metrics.malformed(); reject("malformed message from '" + sourceName + "': " + e.getMessage()); return; }

        Player player = source.getPlayer();
        if (message instanceof HealthPing ping) {
            if (!validPlayer(source, player, ping.playerUuid())) {
                sendResponse(source, new TransferResponse(ping.requestId(), TransferResult.PLAYER_MISMATCH, "player mismatch"));
                return;
            }
            metrics.healthCheck();
            sendResponse(source, new HealthPong(ping.requestId(), sourceName, Protocol.VERSION));
            debug("Health check from " + player.getUsername() + " on " + sourceName);
            return;
        }
        if (!(message instanceof ConnectRequest request)) { reject("unexpected backend message " + message.getClass().getSimpleName()); return; }
        if (!validPlayer(source, player, request.playerUuid())) {
            metrics.rejected(); sendResponse(source, new TransferResponse(request.requestId(), TransferResult.PLAYER_MISMATCH, "player mismatch")); return;
        }
        if (!rateLimiter.allow(player.getUniqueId(), currentConfig.rateLimitMs())) {
            metrics.rateLimited();
            sendResponse(source, new TransferResponse(request.requestId(), TransferResult.RATE_LIMITED, "cooldown"));
            return;
        }
        Optional<com.velocitypowered.api.proxy.server.RegisteredServer> target = proxy.getServer(request.serverName());
        if (target.isEmpty()) {
            metrics.rejected();
            logger.warn("NPC '{}' points to unknown Velocity server '{}'.", request.npcId(), request.serverName());
            sendResponse(source, new TransferResponse(request.requestId(), TransferResult.UNKNOWN_SERVER, request.serverName()));
            return;
        }
        String validated = target.get().getServerInfo().getName();
        debug("CONNECT id=" + request.requestId() + " from=" + sourceName + " player=" + player.getUsername()
                + " npc=" + request.npcId() + " target=" + validated);
        proxy.getCommandManager().executeAsync(player, "server " + validated).whenComplete((executed, error) -> {
            if (error != null || !Boolean.TRUE.equals(executed)) {
                metrics.rejected();
                logger.error("Could not execute /server {} as {}", validated, player.getUsername(), error);
                sendResponse(source, new TransferResponse(request.requestId(), TransferResult.COMMAND_FAILED, validated));
            } else {
                metrics.dispatched();
                sendResponse(source, new TransferResponse(request.requestId(), TransferResult.DISPATCHED, validated));
            }
        });
    }

    @Subscribe public void onDisconnect(DisconnectEvent event) { rateLimiter.remove(event.getPlayer().getUniqueId()); }

    private boolean validPlayer(ServerConnection source, Player player, java.util.UUID uuid) {
        if (!player.getUniqueId().equals(uuid) || proxy.getPlayer(uuid).filter(player::equals).isEmpty()) return false;
        ServerConnection current = player.getCurrentServer().orElse(null);
        return current != null && current.equals(source)
                && current.getServerInfo().getName().equalsIgnoreCase(source.getServerInfo().getName());
    }

    private void sendResponse(ServerConnection source, ProtocolMessage response) {
        try {
            if (!source.sendPluginMessage(channel.get(), ProtocolCodec.encode(response))) {
                metrics.rejected(); debug("Backend refused response id=" + response.requestId());
            }
        } catch (RuntimeException e) { metrics.rejected(); logger.warn("Could not send bridge response", e); }
    }

    private void reject(String reason) { metrics.rejected(); debug("Rejected " + reason); }
    private void debug(String message) { if (config.get().debug()) logger.info("[debug] {}", message); }
}
