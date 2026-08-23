package dev.limbonpc.limbo.bridge;

import com.loohp.limbo.player.Player;
import dev.limbonpc.common.protocol.ConnectRequest;
import dev.limbonpc.common.protocol.ProtocolCodec;
import dev.limbonpc.limbo.npc.RuntimeNpc;
import java.io.IOException;
import net.kyori.adventure.key.Key;

public final class VelocityBridgeClient {
    private final Key channel;
    public VelocityBridgeClient(String channel) { this.channel = Key.key(channel); }

    public void requestServerTransfer(Player player, RuntimeNpc npc) {
        byte[] payload = ProtocolCodec.encode(new ConnectRequest(player.getUniqueId(), npc.definition().id(), npc.definition().server()));
        try { player.sendPluginMessage(channel, payload); }
        catch (IOException e) { System.err.println("[LimboNPC] Failed to send transfer request: " + e.getMessage()); }
    }
}
