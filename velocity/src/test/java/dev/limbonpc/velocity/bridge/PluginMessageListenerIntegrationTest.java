package dev.limbonpc.velocity.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.limbonpc.common.protocol.ConnectRequest;
import dev.limbonpc.common.protocol.HealthPing;
import dev.limbonpc.common.protocol.HealthPong;
import dev.limbonpc.common.protocol.ProtocolCodec;
import dev.limbonpc.common.protocol.ProtocolMessage;
import dev.limbonpc.common.protocol.TransferResponse;
import dev.limbonpc.common.protocol.TransferResult;
import dev.limbonpc.velocity.config.VelocityConfig;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class PluginMessageListenerIntegrationTest {
    private final MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from("limbo-npc:main");
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final ServerConnection source = mock(ServerConnection.class);
    private final ChannelMessageSink target = mock(ChannelMessageSink.class);
    private final Player player = mock(Player.class);
    private final CommandManager commands = mock(CommandManager.class);
    private final RegisteredServer survival = mock(RegisteredServer.class);
    private final UUID uuid = UUID.randomUUID();
    private final VelocityMetrics metrics = new VelocityMetrics();
    private VelocityConfig config;
    private PluginMessageListener listener;

    @BeforeEach void setup() {
        config = new VelocityConfig("limbo-npc:main", Set.of("limbo"), true, 500, Map.of());
        listener = new PluginMessageListener(proxy, mock(Logger.class), () -> config, () -> channel, metrics);
        when(source.getServerInfo()).thenReturn(new ServerInfo("limbo", new InetSocketAddress("127.0.0.1", 30000)));
        when(source.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("Tester");
        when(player.getCurrentServer()).thenReturn(Optional.of(source));
        when(proxy.getPlayer(uuid)).thenReturn(Optional.of(player));
        when(proxy.getCommandManager()).thenReturn(commands);
        when(source.sendPluginMessage(eq(channel), any(byte[].class))).thenReturn(true);
    }

    @Test void validConnectExecutesCanonicalServerCommandAndAcknowledges() throws Exception {
        when(proxy.getServer("survival")).thenReturn(Optional.of(survival));
        when(survival.getServerInfo()).thenReturn(new ServerInfo("survival", new InetSocketAddress("127.0.0.1", 30001)));
        when(commands.executeAsync(player, "server survival")).thenReturn(CompletableFuture.completedFuture(true));

        PluginMessageEvent event = event(new ConnectRequest(42, uuid, "selector", "survival"));
        listener.onPluginMessage(event);

        verify(commands).executeAsync(player, "server survival");
        TransferResponse response = (TransferResponse) capturedResponse();
        assertEquals(42, response.requestId());
        assertEquals(TransferResult.DISPATCHED, response.result());
        assertEquals(PluginMessageEvent.ForwardResult.handled(), event.getResult());
        assertEquals(1, metrics.dispatchedCount());
    }

    @Test void unknownServerAndRapidRepeatAreRejectedWithResponses() throws Exception {
        when(proxy.getServer("missing")).thenReturn(Optional.empty());
        listener.onPluginMessage(event(new ConnectRequest(1, uuid, "bad", "missing")));
        TransferResponse unknown = (TransferResponse) capturedResponse();
        assertEquals(TransferResult.UNKNOWN_SERVER, unknown.result());

        reset(source);
        when(source.getServerInfo()).thenReturn(new ServerInfo("limbo", new InetSocketAddress("127.0.0.1", 30000)));
        when(source.getPlayer()).thenReturn(player);
        when(source.sendPluginMessage(eq(channel), any(byte[].class))).thenReturn(true);
        listener.onPluginMessage(event(new ConnectRequest(2, uuid, "again", "missing")));
        TransferResponse limited = (TransferResponse) capturedResponse();
        assertEquals(TransferResult.RATE_LIMITED, limited.result());
    }

    @Test void healthProbeReturnsBackendAndProtocol() throws Exception {
        listener.onPluginMessage(event(new HealthPing(9, uuid)));
        HealthPong pong = (HealthPong) capturedResponse();
        assertEquals(9, pong.requestId());
        assertEquals("limbo", pong.backend());
        assertEquals(1, metrics.healthCheckCount());
    }

    private PluginMessageEvent event(ProtocolMessage message) {
        return new PluginMessageEvent(source, target, channel, ProtocolCodec.encode(message));
    }

    private ProtocolMessage capturedResponse() throws Exception {
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(source).sendPluginMessage(eq(channel), bytes.capture());
        return ProtocolCodec.decodeMessage(bytes.getValue());
    }
}
