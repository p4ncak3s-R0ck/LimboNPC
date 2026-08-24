package dev.limbonpc.limbo.npc;

import com.loohp.limbo.Limbo;

public final class LimboCompatibility {
    private LimboCompatibility() {}

    public static void verify() {
        int protocol = LimboRuntime.protocol();
        if (protocol < 763 || protocol > 776) {
            throw new IllegalStateException("Unsupported Minecraft protocol " + protocol
                    + ". LimboNPC supports protocols 763-776 (Minecraft 1.20 through 26.2). Refusing to send entity packets.");
        }
        String implementation = LimboRuntime.implementationVersion();
        if (implementation == null || implementation.isBlank()) {
            throw new IllegalStateException("Limbo did not report an implementation version; NPC rendering was disabled safely.");
        }
    }
}
