package dev.limbonpc.limbo.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loohp.limbo.Limbo;
import com.loohp.limbo.events.EventHandler;
import com.loohp.limbo.events.Listener;
import com.loohp.limbo.events.player.PlayerJoinEvent;
import com.loohp.limbo.events.player.PlayerQuitEvent;
import com.loohp.limbo.network.ChannelPacketHandler;
import com.loohp.limbo.network.ChannelPacketRead;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.utils.DataTypeIO;
import dev.limbonpc.limbo.bridge.VelocityBridgeClient;
import java.io.DataInput;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;

public final class NpcInteractionAdapter implements Listener {
    private static final Key HANDLER_KEY = Key.key("limbo-npc", "entity_interaction");
    private final NpcManager npcs;
    private final VelocityBridgeClient bridge;
    private final long cooldownMs;
    private final Map<UUID, Long> lastInteraction = new ConcurrentHashMap<>();
    private final int interactPacketId;
    private final int attackPacketId;

    public NpcInteractionAdapter(NpcManager npcs, VelocityBridgeClient bridge, long cooldownMs) {
        this.npcs = npcs; this.bridge = bridge; this.cooldownMs = Math.max(0, cooldownMs);
        int discoveredInteract = packetId("minecraft:interact");
        this.interactPacketId = discoveredInteract >= 0 ? discoveredInteract : legacyInteractPacketId(Limbo.getInstance().SERVER_IMPLEMENTATION_PROTOCOL);
        this.attackPacketId = packetId("minecraft:attack");
        if (interactPacketId < 0 && attackPacketId < 0) {
            throw new IllegalStateException("Unsupported Limbo protocol " + Limbo.getInstance().SERVER_IMPLEMENTATION_PROTOCOL);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.clientConnection.getChannel().addHandlerBefore(HANDLER_KEY, new ChannelPacketHandler() {
            @Override public ChannelPacketRead read(ChannelPacketRead read) {
                if (read.hasReadPacket() || (read.getPacketId() != interactPacketId && read.getPacketId() != attackPacketId)) return read;
                try {
                    DataInput input = read.getDataInput();
                    int entityId = readVarInt(input);
                    int consumed = DataTypeIO.getVarIntLength(read.getPacketId()) + DataTypeIO.getVarIntLength(entityId);
                    int remaining = Math.max(0, read.getSize() - consumed);
                    input.skipBytes(remaining);
                    npcs.getByEntityId(entityId).ifPresent(npc -> click(player, npc));
                    return null;
                } catch (Exception e) {
                    System.err.println("[LimboNPC] Failed to decode interaction from " + player.getName() + ": " + e.getMessage());
                    return null;
                }
            }
        });
        npcs.refreshFor(player);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        unregister(event.getPlayer());
    }

    public void unregister(Player player) {
        player.clientConnection.getChannel().removeHandler(HANDLER_KEY);
        lastInteraction.remove(player.getUniqueId());
    }

    private void click(Player player, RuntimeNpc npc) {
        long now = System.currentTimeMillis();
        Long previous = lastInteraction.put(player.getUniqueId(), now);
        if (previous != null && now - previous < cooldownMs) return;
        bridge.requestServerTransfer(player, npc);
    }

    private static int readVarInt(DataInput input) throws Exception {
        int result = 0;
        for (int position = 0; position < 5; position++) {
            int value = input.readUnsignedByte();
            result |= (value & 0x7F) << (position * 7);
            if ((value & 0x80) == 0) return result;
        }
        throw new IllegalArgumentException("VarInt is too large");
    }

    private static int legacyInteractPacketId(int protocol) {
        return switch (protocol) {
            case 763 -> 0x10;                 // 1.20-1.20.1
            case 764 -> 0x12;                 // 1.20.2
            case 765 -> 0x13;                 // 1.20.3-1.20.4
            case 766, 767 -> 0x16;            // 1.20.5-1.21.1
            case 768, 769, 770 -> 0x18;       // 1.21.2-1.21.5
            case 771, 772, 773, 774, 775 -> 0x19; // 1.21.6-1.21.11
            case 776 -> 0x1A;                 // 26.x fallback
            default -> -1;
        };
    }

    private static int packetId(String key) {
        try (InputStream in = NpcInteractionAdapter.class.getClassLoader().getResourceAsStream("reports/packets.json")) {
            if (in == null) return -1;
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject serverbound = root.getAsJsonObject("play").getAsJsonObject("serverbound");
            if (!serverbound.has(key)) return -1;
            return serverbound.getAsJsonObject(key).get("protocol_id").getAsInt();
        } catch (Exception e) { return -1; }
    }
}
