package dev.limbonpc.velocity.bridge;

import java.util.concurrent.atomic.LongAdder;

public final class VelocityMetrics {
    private final LongAdder received = new LongAdder();
    private final LongAdder dispatched = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder malformed = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder healthChecks = new LongAdder();

    public void received() { received.increment(); }
    public void dispatched() { dispatched.increment(); }
    public void rejected() { rejected.increment(); }
    public void malformed() { malformed.increment(); }
    public void rateLimited() { rateLimited.increment(); }
    public void healthCheck() { healthChecks.increment(); }
    public long receivedCount() { return received.sum(); }
    public long dispatchedCount() { return dispatched.sum(); }
    public long rejectedCount() { return rejected.sum(); }
    public long malformedCount() { return malformed.sum(); }
    public long rateLimitedCount() { return rateLimited.sum(); }
    public long healthCheckCount() { return healthChecks.sum(); }
}
