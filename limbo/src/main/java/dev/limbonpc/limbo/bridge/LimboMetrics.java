package dev.limbonpc.limbo.bridge;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class LimboMetrics {
    private final LongAdder clicks = new LongAdder();
    private final LongAdder requests = new LongAdder();
    private final LongAdder acknowledgements = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final AtomicLong lastResponseAt = new AtomicLong();

    public void click() { clicks.increment(); }
    public void request() { requests.increment(); }
    public void acknowledgement() { acknowledgements.increment(); lastResponseAt.set(System.currentTimeMillis()); }
    public void timeout() { timeouts.increment(); }
    public void error() { errors.increment(); }
    public long clicks() { return clicks.sum(); }
    public long requests() { return requests.sum(); }
    public long acknowledgements() { return acknowledgements.sum(); }
    public long timeouts() { return timeouts.sum(); }
    public long errors() { return errors.sum(); }
    public long lastResponseAt() { return lastResponseAt.get(); }
}
