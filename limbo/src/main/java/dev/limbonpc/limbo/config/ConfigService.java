package dev.limbonpc.limbo.config;

import dev.limbonpc.limbo.npc.NpcDefinition;
import dev.limbonpc.limbo.npc.NpcLocation;
import dev.limbonpc.limbo.npc.NpcSkin;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class ConfigService {
    private final Path directory;
    private final Yaml yaml;

    public ConfigService(Path directory) {
        this.directory = directory;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        this.yaml = new Yaml(options);
    }

    public Path directory() { return directory; }

    public LimboConfig loadConfig() throws IOException {
        Files.createDirectories(directory.resolve("skins"));
        Path file = directory.resolve("config.yml");
        if (Files.notExists(file)) copyResource("config.yml", file);
        Map<String, Object> root = loadMap(file);
        Map<String, Object> bridge = map(root.get("bridge"));
        Map<String, Object> npc = map(root.get("npc"));
        Map<String, Object> messages = map(root.get("messages"));
        LimboConfig defaults = LimboConfig.defaults();
        return new LimboConfig(string(bridge, "channel", defaults.channel()),
                longValue(npc, "interaction-cooldown-ms", defaults.interactionCooldownMs()),
                bool(npc, "look-at-player", defaults.lookAtPlayer()),
                strings(npc.get("default-hologram"), defaults.defaultHologram()),
                string(messages, "prefix", defaults.prefix()));
    }

    public Map<String, NpcDefinition> loadNpcs() throws IOException {
        Path file = directory.resolve("npcs.yml");
        if (Files.notExists(file)) copyResource("npcs.yml", file);
        Map<String, Object> root = loadMap(file);
        Map<String, Object> section = map(root.get("npcs"));
        Map<String, NpcDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            try {
                String id = entry.getKey().toLowerCase(Locale.ROOT);
                Map<String, Object> data = map(entry.getValue());
                Map<String, Object> loc = map(data.get("location"));
                NpcLocation location = new NpcLocation(string(loc, "world", "world"), number(loc, "x", 0),
                        number(loc, "y", 0), number(loc, "z", 0), (float) number(loc, "yaw", 0),
                        (float) number(loc, "pitch", 0));
                NpcSkin skin = parseSkin(map(data.get("skin")));
                NpcDefinition definition = new NpcDefinition(id, bool(data, "enabled", true),
                        string(data, "server", id), string(data, "display-name", id), location, skin,
                        strings(data.get("hologram"), List.of()));
                result.put(id, definition);
            } catch (RuntimeException e) {
                System.err.println("[LimboNPC] Skipping invalid NPC '" + entry.getKey() + "': " + e.getMessage());
            }
        }
        return result;
    }

    public synchronized void saveNpcs(Iterable<NpcDefinition> definitions) throws IOException {
        Map<String, Object> npcs = new LinkedHashMap<>();
        for (NpcDefinition npc : definitions) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", npc.enabled());
            data.put("server", npc.server());
            data.put("display-name", npc.displayName());
            NpcLocation l = npc.location();
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("world", l.world()); location.put("x", l.x()); location.put("y", l.y());
            location.put("z", l.z()); location.put("yaw", l.yaw()); location.put("pitch", l.pitch());
            data.put("location", location);
            data.put("skin", skinMap(npc.skin()));
            data.put("hologram", new ArrayList<>(npc.hologram()));
            npcs.put(npc.id(), data);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1); root.put("npcs", npcs);
        atomicWrite(directory.resolve("npcs.yml"), yaml.dump(root));
    }

    private NpcSkin parseSkin(Map<String, Object> value) {
        String type = string(value, "type", "none").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "USERNAME" -> NpcSkin.username(string(value, "username", ""), nullable(value.get("value")), nullable(value.get("signature")));
            case "TEXTURE" -> NpcSkin.texture(string(value, "value", ""), string(value, "signature", ""));
            default -> NpcSkin.none();
        };
    }

    private Map<String, Object> skinMap(NpcSkin skin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", skin.type().name().toLowerCase(Locale.ROOT));
        if (skin.type() == NpcSkin.Type.USERNAME) map.put("username", skin.username());
        if (skin.hasTexture()) { map.put("value", skin.value()); map.put("signature", skin.signature()); }
        return map;
    }

    private void copyResource(String name, Path destination) throws IOException {
        try (InputStream in = ConfigService.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IOException("Missing bundled resource " + name);
            Files.copy(in, destination);
        }
    }

    @SuppressWarnings("unchecked") private Map<String, Object> loadMap(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object value = yaml.load(in);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
        }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static String string(Map<String, Object> map, String key, String fallback) { Object v = map.get(key); return v == null ? fallback : String.valueOf(v); }
    private static String nullable(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) { Object v = map.get(key); return v instanceof Boolean b ? b : v == null ? fallback : Boolean.parseBoolean(String.valueOf(v)); }
    private static long longValue(Map<String, Object> map, String key, long fallback) { Object v = map.get(key); return v instanceof Number n ? n.longValue() : fallback; }
    private static double number(Map<String, Object> map, String key, double fallback) { Object v = map.get(key); return v instanceof Number n ? n.doubleValue() : fallback; }
    private static List<String> strings(Object value, List<String> fallback) { if (!(value instanceof List<?> list)) return fallback; return list.stream().map(String::valueOf).toList(); }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
