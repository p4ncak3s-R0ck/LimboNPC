package dev.limbonpc.limbo.config;

import java.util.List;
import java.util.Map;

public record LimboConfig(String channel, long interactionCooldownMs, long acknowledgementTimeoutMs,
                          boolean lookAtPlayer, boolean debug, List<String> defaultHologram,
                          String prefix, Map<String, String> messages) {
    public LimboConfig { defaultHologram = List.copyOf(defaultHologram); messages = Map.copyOf(messages); }

    public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }

    public static LimboConfig defaults() {
        return new LimboConfig("limbo-npc:main", 500L, 2500L, false, false,
                List.of("<gray>Click to join!"), "<dark_gray>[<green>LimboNPC<dark_gray>] ", Map.ofEntries(
                Map.entry("no-permission", "<red>You do not have permission."),
                Map.entry("player-only", "<red>This command can only be used by a player."),
                Map.entry("npc-not-found", "<red>NPC '<id>' does not exist."),
                Map.entry("bridge-timeout", "<red>The server selector bridge did not respond."),
                Map.entry("unknown-server", "<red>That server is currently unavailable."),
                Map.entry("rate-limited", "<yellow>Please wait before selecting another server."),
                Map.entry("transfer-failed", "<red>The server transfer could not be started."),
                Map.entry("status-probe", "<gray>Checking the Velocity bridge..."),
                Map.entry("status-online", "<green>Velocity bridge online via '<backend>' (protocol <protocol>)."),
                Map.entry("npc-enabled", "<green>Enabled NPC '<id>'."),
                Map.entry("npc-disabled", "<green>Disabled NPC '<id>'."),
                Map.entry("npc-created", "<green>Created NPC '<white><id><green>' targeting '<white><server><green>'."),
                Map.entry("npc-removed", "<green>Removed NPC '<white><id><green>'."),
                Map.entry("npc-moved", "<green>Moved NPC '<white><id><green>'."),
                Map.entry("server-updated", "<green>NPC '<id>' now targets '<server>'."),
                Map.entry("name-updated", "<green>Display name updated for '<id>'."),
                Map.entry("hologram-updated", "<green>Hologram updated for '<id>'."),
                Map.entry("skin-cleared", "<green>Skin cleared for '<id>'."),
                Map.entry("skin-updated", "<green>Skin updated for '<id>'."),
                Map.entry("skin-cached", "<green>Skin for '<id>' updated from cache."),
                Map.entry("skin-resolving", "<gray>Resolving skin for <username>..."),
                Map.entry("skin-lookup-failed", "<red>Skin lookup failed: <error>"),
                Map.entry("skin-save-failed", "<red>Could not save skin: <error>"),
                Map.entry("operation-failed", "<red>Operation failed: <error>"),
                Map.entry("reloaded", "<green>Configuration reloaded.")));
    }
}
