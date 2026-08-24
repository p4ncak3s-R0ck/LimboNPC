package dev.limbonpc.limbo.npc;

import com.loohp.limbo.Limbo;

/** Reads Limbo version fields reflectively so compile-time constants are not inlined across compatibility builds. */
public final class LimboRuntime {
    private LimboRuntime() {}

    public static int protocol() {
        try { return Limbo.class.getField("SERVER_IMPLEMENTATION_PROTOCOL").getInt(Limbo.getInstance()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read Limbo protocol version", e); }
    }

    public static String implementationVersion() {
        try { return String.valueOf(Limbo.class.getField("SERVER_IMPLEMENTATION_VERSION").get(Limbo.getInstance())); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read Limbo implementation version", e); }
    }
}
