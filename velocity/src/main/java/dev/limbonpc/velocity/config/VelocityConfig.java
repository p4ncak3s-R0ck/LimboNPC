package dev.limbonpc.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

public record VelocityConfig(String channel, Set<String> trustedLimboServers, boolean debug) {
    public VelocityConfig {
        trustedLimboServers = Set.copyOf(trustedLimboServers);
    }

    @SuppressWarnings("unchecked")
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
        try (InputStream in = Files.newInputStream(file)) { root = new Yaml().load(in); }
        if (root == null) root = Map.of();
        String channel = String.valueOf(root.getOrDefault("channel", "limbo-npc:main"));
        boolean debug = Boolean.parseBoolean(String.valueOf(root.getOrDefault("debug", false)));
        Set<String> trusted = new HashSet<>();
        Object list = root.get("trusted-limbo-servers");
        if (list instanceof List<?> values) for (Object value : values) trusted.add(String.valueOf(value).toLowerCase(Locale.ROOT));
        if (trusted.isEmpty()) trusted.add("limbo");
        return new VelocityConfig(channel, trusted, debug);
    }

    public boolean trusts(String server) { return trustedLimboServers.contains(server.toLowerCase(Locale.ROOT)); }
}
