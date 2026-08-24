package dev.limbonpc.velocity.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {
    @Test void limitsPerPlayerAndExpires() {
        AtomicLong time = new AtomicLong(1_000);
        RequestRateLimiter limiter = new RequestRateLimiter(time::get);
        UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        assertTrue(limiter.allow(first, 500));
        assertFalse(limiter.allow(first, 500));
        assertTrue(limiter.allow(second, 500));
        time.addAndGet(499); assertFalse(limiter.allow(first, 500));
        time.incrementAndGet(); assertTrue(limiter.allow(first, 500));
        limiter.remove(first); assertTrue(limiter.allow(first, 500));
    }

    @Test void disabledLimiterAlwaysAllows() {
        RequestRateLimiter limiter = new RequestRateLimiter(() -> 1);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.allow(player, 0)); assertTrue(limiter.allow(player, 0));
    }
}
