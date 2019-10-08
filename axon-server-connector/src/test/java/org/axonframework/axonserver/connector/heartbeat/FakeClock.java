package org.axonframework.axonserver.connector.heartbeat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class FakeClock extends Clock {

    private final Supplier<Instant> instant;
    private final ZoneId zone;

    public FakeClock(Supplier<Instant> instant) {
        this(instant, ZoneId.systemDefault());
    }

    public FakeClock(Supplier<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) {
            return this;
        }
        return new FakeClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
