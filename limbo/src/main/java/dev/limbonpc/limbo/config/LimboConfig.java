package dev.limbonpc.limbo.config;

import java.util.List;

public record LimboConfig(String channel, long interactionCooldownMs, boolean lookAtPlayer,
                          List<String> defaultHologram, String prefix) {
    public static LimboConfig defaults() {
        return new LimboConfig("limbo-npc:main", 500L, false, List.of("<gray>Click to join!"),
                "<dark_gray>[<green>LimboNPC<dark_gray>] ");
    }
}
