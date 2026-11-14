package kr.ac.cnu.lsrs;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTestClock extends Clock {
    private final AtomicReference<Instant> value = new AtomicReference<>(Instant.parse("2026-11-14T01:00:00Z"));
    public void set(Instant instant) { value.set(instant); }
    public void advance(Duration duration) { value.updateAndGet(now -> now.plus(duration)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return value.get(); }
}
