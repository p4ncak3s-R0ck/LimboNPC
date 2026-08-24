package dev.limbonpc.limbo.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loohp.limbo.Limbo;
import com.loohp.limbo.entity.ArmorStand;
import com.loohp.limbo.entity.Entity;
import com.loohp.limbo.location.Location;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutEntityDestroy;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutEntityMetadata;
import com.loohp.limbo.network.protocol.packets.PacketOut;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutPlayerInfo;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutPlayerInfo.PlayerInfoAction;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutPlayerInfo.PlayerInfoData.PlayerInfoDataAddPlayer;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutPlayerInfo.PlayerInfoData.PlayerInfoDataAddPlayer.PlayerSkinProperty;
import com.loohp.limbo.network.protocol.packets.PacketPlayOutSpawnEntity;
import com.loohp.limbo.player.Player;
import com.loohp.limbo.utils.GameMode;
import com.loohp.limbo.world.World;
import dev.limbonpc.limbo.config.ConfigService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class NpcManager {
    private final ConfigService repository;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final int playerEntityTypeId;
    private final int armorStandEntityTypeId;
    private final Map<String, RuntimeNpc> byId = new LinkedHashMap<>();
    private final Map<Integer, RuntimeNpc> byEntityId = new ConcurrentHashMap<>();

    public NpcManager(ConfigService repository) {
        this.repository = repository;
        int protocol = LimboRuntime.protocol();
        this.playerEntityTypeId = entityTypeId("minecraft:player", legacyPlayerTypeId(protocol));
        this.armorStandEntityTypeId = entityTypeId("minecraft:armor_stand", legacyArmorStandTypeId(protocol));
        if (playerEntityTypeId < 0 || armorStandEntityTypeId < 0) {
            throw new IllegalStateException("Missing entity registry mappings for protocol " + protocol + "; refusing to render unsafe NPC packets.");
        }
    }

    public synchronized void loadAndSpawn() throws IOException {
        replaceAll(repository.loadNpcs());
    }

    public synchronized void replaceAll(Map<String, NpcDefinition> incoming) {
        for (String id : new ArrayList<>(byId.keySet())) {
            RuntimeNpc current = byId.get(id);
            NpcDefinition next = incoming.get(id);
            if (next == null || !current.definition().equals(next)) despawn(id);
        }
        for (NpcDefinition definition : incoming.values()) {
            RuntimeNpc current = byId.get(definition.id());
            if (current == null) {
                if (definition.enabled()) spawn(definition);
                else byId.put(definition.id(), new RuntimeNpc(definition, null, List.of()));
            }
        }
    }

    public synchronized RuntimeNpc create(NpcDefinition definition) throws IOException {
        if (byId.containsKey(definition.id())) throw new IllegalArgumentException("NPC already exists");
        RuntimeNpc runtime = definition.enabled() ? spawn(definition) : null;
        if (runtime == null) byId.put(definition.id(), new RuntimeNpc(definition, null, List.of()));
        save();
        return runtime;
    }

    public synchronized void remove(String id) throws IOException {
        require(id); despawn(normalize(id)); save();
    }

    public synchronized void update(String id, NpcDefinition definition) throws IOException {
        require(id); despawn(normalize(id));
        if (definition.enabled()) spawn(definition); else byId.put(definition.id(), new RuntimeNpc(definition, null, List.of()));
        save();
    }

    public synchronized Optional<RuntimeNpc> get(String id) { return Optional.ofNullable(byId.get(normalize(id))); }
    public Optional<RuntimeNpc> getByEntityId(int entityId) { return Optional.ofNullable(byEntityId.get(entityId)); }
    public synchronized Collection<RuntimeNpc> all() { return Collections.unmodifiableList(new ArrayList<>(byId.values())); }
    public synchronized Collection<NpcDefinition> definitions() { return byId.values().stream().map(RuntimeNpc::definition).toList(); }

    public synchronized void shutdown() {
        for (String id : new ArrayList<>(byId.keySet())) despawn(id);
    }

    public void refreshFor(Player player) {
        for (RuntimeNpc npc : all()) {
            if (npc.entity() == null) continue;
            try {
                player.clientConnection.sendPacket(new PacketPlayOutEntityDestroy(entityIds(npc)));
                sendRuntime(player, npc);
            } catch (IOException e) {
                System.err.println("[LimboNPC] Could not refresh NPC for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private RuntimeNpc spawn(NpcDefinition definition) {
        World world = Limbo.getInstance().getWorld(definition.location().world());
        if (world == null) {
            System.err.println("[LimboNPC] NPC '" + definition.id() + "' references missing world '" + definition.location().world() + "'.");
            RuntimeNpc skipped = new RuntimeNpc(definition, null, List.of());
            byId.put(definition.id(), skipped);
            return skipped;
        }
        NpcLocation source = definition.location();
        Location location = new Location(world, source.x(), source.y(), source.z(), source.yaw(), source.pitch());
        UUID uuid = UUID.nameUUIDFromBytes(("limbonpc:" + definition.id()).getBytes(StandardCharsets.UTF_8));
        PlayerNpcEntity entity = new PlayerNpcEntity(uuid, location);
        entity.setCustomName(miniMessage.deserialize(definition.displayName()));
        entity.setCustomNameVisible(true);
        refreshMetadata(entity);

        List<ArmorStand> holograms = new ArrayList<>();
        double y = source.y() + 2.35 + Math.max(0, definition.hologram().size() - 1) * 0.27;
        for (String line : definition.hologram()) {
            HologramEntity stand = new HologramEntity(new Location(world, source.x(), y, source.z(), 0, 0));
            stand.setInvisible(true); stand.setMarker(true); stand.setSmall(true); stand.setGravity(false);
            stand.setCustomName(miniMessage.deserialize(line)); stand.setCustomNameVisible(true);
            refreshMetadata(stand);
            holograms.add(stand); y -= 0.27;
        }
        RuntimeNpc runtime = new RuntimeNpc(definition, entity, List.copyOf(holograms));
        byId.put(definition.id(), runtime); byEntityId.put(entity.getEntityId(), runtime);
        for (Player player : world.getPlayers()) {
            if (!player.clientConnection.isReady()) continue;
            try { sendRuntime(player, runtime); }
            catch (IOException e) { System.err.println("[LimboNPC] Could not spawn NPC for " + player.getName() + ": " + e.getMessage()); }
        }
        return runtime;
    }

    private void despawn(String id) {
        RuntimeNpc npc = byId.remove(id);
        if (npc == null || npc.entity() == null) return;
        byEntityId.remove(npc.entityId());
        for (Player player : npc.entity().getWorld().getPlayers()) {
            if (!player.clientConnection.isReady()) continue;
            try { player.clientConnection.sendPacket(new PacketPlayOutEntityDestroy(entityIds(npc))); }
            catch (IOException e) { System.err.println("[LimboNPC] Could not despawn NPC for " + player.getName() + ": " + e.getMessage()); }
        }
    }

    private void sendProfile(Player player, RuntimeNpc npc) throws IOException { sendProfile(player, npc.definition(), npc.entity().getUniqueId()); }
    private void sendProfile(Player player, NpcDefinition definition, UUID uuid) throws IOException {
        Optional<PlayerSkinProperty> property = definition.skin().hasTexture()
                ? Optional.of(new PlayerSkinProperty(definition.skin().value(), definition.skin().signature())) : Optional.empty();
        String profileName = ("NPC_" + definition.id()).substring(0, Math.min(16, 4 + definition.id().length()));
        PlayerInfoDataAddPlayer data = new PlayerInfoDataAddPlayer(profileName, false, property, GameMode.ADVENTURE, 0, false, Optional.empty());
        player.clientConnection.sendPacket(new PacketPlayOutPlayerInfo(EnumSet.of(PlayerInfoAction.ADD_PLAYER,
                PlayerInfoAction.UPDATE_GAME_MODE, PlayerInfoAction.UPDATE_LISTED, PlayerInfoAction.UPDATE_LATENCY), uuid, data));
    }

    private void sendRuntime(Player player, RuntimeNpc npc) throws IOException {
        sendProfile(player, npc);
        sendEntity(player, npc.entity());
        for (ArmorStand hologram : npc.holograms()) sendEntity(player, hologram);
    }

    private void sendEntity(Player player, Entity entity) throws IOException {
        PacketOut spawn = createSpawnPacket(entity);
        int entityTypeId = entity instanceof PlayerNpcEntity ? playerEntityTypeId : armorStandEntityTypeId;
        SynchronizedPacketSender.sendRaw(player.clientConnection, replaceSpawnEntityType(spawn.serializePacket(), entityTypeId));
        player.clientConnection.sendPacket(metadataPacket(entity));
    }

    private static PacketPlayOutEntityMetadata metadataPacket(Entity entity) {
        if (entity instanceof PlayerNpcEntity) {
            return new PacketPlayOutEntityMetadata(entity, false,
                    field(Entity.class, "customName"), field(Entity.class, "customNameVisible"),
                    field(PlayerNpcEntity.class, "skinLayers"));
        }
        return new PacketPlayOutEntityMetadata(entity, false,
                field(Entity.class, "invisible"), field(Entity.class, "customName"),
                field(Entity.class, "customNameVisible"), field(Entity.class, "noGravity"),
                field(ArmorStand.class, "small"), field(ArmorStand.class, "marker"));
    }

    private static Field field(Class<?> type, String name) {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException e) { throw new IllegalStateException("Missing Limbo metadata field " + type.getSimpleName() + "." + name, e); }
    }

    private PacketOut createSpawnPacket(Entity entity) {
        try {
            for (Constructor<?> constructor : PacketPlayOutSpawnEntity.class.getConstructors()) {
                if (constructor.getParameterCount() == 11) {
                    Class<?> vectorType = constructor.getParameterTypes()[10];
                    Object zeroVector = vectorType.getConstructor(double.class, double.class, double.class).newInstance(0D, 0D, 0D);
                    return (PacketOut) constructor.newInstance(entity.getEntityId(), entity.getUniqueId(), entity.getType(),
                            entity.getX(), entity.getY(), entity.getZ(), entity.getPitch(), entity.getYaw(), entity.getYaw(), 0, zeroVector);
                }
                if (constructor.getParameterCount() == 13) {
                    return (PacketOut) constructor.newInstance(entity.getEntityId(), entity.getUniqueId(), entity.getType(),
                            entity.getX(), entity.getY(), entity.getZ(), entity.getPitch(), entity.getYaw(), entity.getYaw(), 0,
                            (short) 0, (short) 0, (short) 0);
                }
            }
            throw new NoSuchMethodException("Unsupported PacketPlayOutSpawnEntity constructor");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create NPC spawn packet for this Limbo version", e);
        }
    }

    private static byte[] replaceSpawnEntityType(byte[] packet, int entityTypeId) {
        if (entityTypeId < 0) throw new IllegalStateException("No entity type mapping is available for this Limbo version");
        int offset = skipVarInt(packet, 0);       // packet ID
        offset = skipVarInt(packet, offset);     // entity ID
        int typeStart = offset + 16;             // UUID
        int typeEnd = skipVarInt(packet, typeStart);
        ByteArrayOutputStream output = new ByteArrayOutputStream(packet.length + 2);
        output.write(packet, 0, typeStart);
        writeVarInt(output, entityTypeId);
        output.write(packet, typeEnd, packet.length - typeEnd);
        return output.toByteArray();
    }

    private static int skipVarInt(byte[] data, int offset) {
        for (int i = 0; i < 5 && offset + i < data.length; i++) {
            if ((data[offset + i] & 0x80) == 0) return offset + i + 1;
        }
        throw new IllegalArgumentException("Malformed spawn packet VarInt");
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static int entityTypeId(String key, int fallback) {
        try (InputStream in = NpcManager.class.getClassLoader().getResourceAsStream("reports/registries.json")) {
            if (in == null) return fallback;
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("minecraft:entity_type").getAsJsonObject("entries");
            return entries.getAsJsonObject(key).get("protocol_id").getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int legacyPlayerTypeId(int protocol) {
        return switch (protocol) {
            case 763, 764 -> 122;
            case 765 -> 124;
            case 766, 767 -> 128;
            default -> -1;
        };
    }

    private static int legacyArmorStandTypeId(int protocol) {
        return switch (protocol) {
            case 763, 764, 765 -> 2;
            case 766, 767 -> 3;
            default -> -1;
        };
    }

    private static int[] entityIds(RuntimeNpc npc) {
        int[] ids = new int[1 + npc.holograms().size()];
        ids[0] = npc.entityId();
        for (int i = 0; i < npc.holograms().size(); i++) ids[i + 1] = npc.holograms().get(i).getEntityId();
        return ids;
    }

    private static void refreshMetadata(Entity entity) {
        try { entity.getDataWatcher().update(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Could not prepare NPC metadata", e); }
    }

    private RuntimeNpc require(String id) { RuntimeNpc value = byId.get(normalize(id)); if (value == null) throw new IllegalArgumentException("NPC not found"); return value; }
    private static String normalize(String id) { return id.toLowerCase(Locale.ROOT); }
    private void save() throws IOException { repository.saveNpcs(definitions()); }
}
