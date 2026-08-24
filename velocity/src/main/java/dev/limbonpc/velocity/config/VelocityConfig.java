package dev.limbonpc.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

public record VelocityConfig(String channel, Set<String> trustedLimboServers, boolean debug,
                             long rateLimitMs, Map<String, String> messages) {
    public VelocityConfig {
        trustedLimboServers = Set.copyOf(trustedLimboServers); messages = Map.copyOf(messages);
    }

    public static VelocityConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("config.yml");
        if (Files.notExists(file)) {
            try (InputStream in = VelocityConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (in == null) throw new IOException("Missing bundled config.yml");
                Files.copy(in, file);
            }
        }
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) { root = map(new Yaml().load(in)); }
        String channel = String.valueOf(root.getOrDefault("channel", "limbo-npc:main"));
        boolean debug = Boolean.parseBoolean(String.valueOf(root.getOrDefault("debug", false)));
        Set<String> trusted = new HashSet<>();
        Object list = root.get("trusted-limbo-servers");
        if (list instanceof List<?> values) for (Object value : values) trusted.add(String.valueOf(value).toLowerCase(Locale.ROOT));
        if (trusted.isEmpty()) trusted.add("limbo");
        Map<String, Object> rateLimit = map(root.get("rate-limit"));
        long cooldown = number(rateLimit.get("cooldown-ms"), 500L);
        Map<String, String> messages = new HashMap<>();
        messages.put("unknown-server", "That server is currently unavailable.");
        messages.put("rate-limited", "Please wait before selecting another server.");
        messages.put("reload-success", "LimboNPC configuration reloaded.");
        messages.put("reload-failed", "LimboNPC reload failed; check the proxy log.");
        for (Map.Entry<String, Object> entry : map(root.get("messages")).entrySet()) messages.put(entry.getKey(), String.valueOf(entry.getValue()));
        return new VelocityConfig(channel, trusted, debug, Math.max(0, cooldown), messages);
    }

    public boolean trusts(String server) { return trustedLimboServers.contains(server.toLowerCase(Locale.ROOT)); }
    public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static long number(Object value, long fallback) { return value instanceof Number n ? n.longValue() : fallback; }
}
