package dev.limbonpc.limbo.npc;

import com.loohp.limbo.network.ClientConnection;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;

/** Coordinates patched raw packets with Limbo's normal packet writer where its send lock is available. */
final class SynchronizedPacketSender {
    private static final Field SEND_LOCK = findSendLock();

    private SynchronizedPacketSender() {}

    static void sendRaw(ClientConnection connection, byte[] packet) throws IOException {
        Lock lock = lock(connection);
        if (lock != null) lock.lock();
        try {
            synchronized (connection.getChannel()) {
                connection.getChannel().writePacketRaw(packet);
            }
        } finally {
            if (lock != null) lock.unlock();
        }
    }

    private static Field findSendLock() {
        try {
            Field field = ClientConnection.class.getDeclaredField("packetSendLock");
            field.setAccessible(true); return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Lock lock(ClientConnection connection) {
        if (SEND_LOCK == null) return null;
        try { return (Lock) SEND_LOCK.get(connection); }
        catch (IllegalAccessException | ClassCastException ignored) { return null; }
    }
}
