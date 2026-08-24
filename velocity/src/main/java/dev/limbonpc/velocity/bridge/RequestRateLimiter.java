package dev.limbonpc.velocity.bridge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class RequestRateLimiter {
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RequestRateLimiter() { this(System::currentTimeMillis); }
    RequestRateLimiter(LongSupplier clock) { this.clock = clock; }

    public boolean allow(UUID player, long cooldownMs) {
        if (cooldownMs <= 0) return true;
        long now = clock.getAsLong();
        AtomicBoolean allowed = new AtomicBoolean();
        lastRequest.compute(player, (uuid, previous) -> {
            if (previous == null || now - previous >= cooldownMs) { allowed.set(true); return now; }
            return previous;
        });
        return allowed.get();
    }

    public void remove(UUID player) { lastRequest.remove(player); }
    public int trackedPlayers() { return lastRequest.size(); }
}
