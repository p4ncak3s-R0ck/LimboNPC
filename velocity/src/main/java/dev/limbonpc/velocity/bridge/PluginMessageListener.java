package dev.limbonpc.velocity.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import dev.limbonpc.common.protocol.ConnectRequest;
import dev.limbonpc.common.protocol.ProtocolCodec;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.util.Optional;
import org.slf4j.Logger;

public final class PluginMessageListener {
    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityConfig config;
    private final ChannelIdentifier channel;

    public PluginMessageListener(ProxyServer proxy, Logger logger, VelocityConfig config, ChannelIdentifier channel) {
        this.proxy = proxy; this.logger = logger; this.config = config; this.channel = channel;
    }

    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source)) {
            debug("Rejected client-originated plugin message"); return;
        }
        String sourceName = source.getServerInfo().getName();
        if (!config.trusts(sourceName)) { debug("Rejected message from untrusted backend '" + sourceName + "'"); return; }
        ConnectRequest request;
        try { request = ProtocolCodec.decode(event.getData()); }
        catch (ProtocolCodec.ProtocolException e) { debug("Rejected malformed message from '" + sourceName + "': " + e.getMessage()); return; }
        Player player = source.getPlayer();
        if (!player.getUniqueId().equals(request.playerUuid()) || proxy.getPlayer(request.playerUuid()).filter(player::equals).isEmpty()) {
            debug("Rejected request with mismatched player UUID " + request.playerUuid()); return;
        }
        ServerConnection current = player.getCurrentServer().orElse(null);
        if (current == null || !current.getServerInfo().getName().equalsIgnoreCase(sourceName)) {
            debug("Rejected stale backend request for " + player.getUsername()); return;
        }
        Optional<com.velocitypowered.api.proxy.server.RegisteredServer> target = proxy.getServer(request.serverName());
        if (target.isEmpty()) {
            logger.warn("NPC '{}' points to unknown Velocity server '{}'.", request.npcId(), request.serverName());
            player.sendMessage(net.kyori.adventure.text.Component.text("That server is currently unavailable."));
            return;
        }
        String validated = target.get().getServerInfo().getName();
        debug("CONNECT from " + sourceName + ": " + player.getUsername() + " via '" + request.npcId() + "' -> " + validated);
        proxy.getCommandManager().executeAsync(player, "server " + validated).exceptionally(error -> {
            logger.error("Could not execute /server {} as {}", validated, player.getUsername(), error); return false;
        });
    }

    private void debug(String message) { if (config.debug()) logger.info("[debug] {}", message); }
}
