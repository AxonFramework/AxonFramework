package org.axonframework.micrometer.reservoir;

import io.micrometer.core.instrument.Clock;
import org.junit.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Adapted from com.codahale.metrics.SlidingTimeWindowReservoirTest from io.dropwizard.metrics:metrics-core:3.1.2
 */
public class SlidingTimeWindowReservoirTest {
    private final Clock clock = mock(Clock.class);
    private final SlidingTimeWindowReservoir reservoir = new SlidingTimeWindowReservoir(10, TimeUnit.NANOSECONDS, clock);

    @Test
    public void storesMeasurementsWithDuplicateTicks() {
        when(clock.wallTime()).thenReturn(20L);

        reservoir.update(1L);
        reservoir.update(2L);

        assertEquals(Arrays.asList(1L, 2L), reservoir.getMeasurements());
    }

    @Test
    public void boundsMeasurementsToATimeWindow() {
        when(clock.wallTime()).thenReturn(0L);
        reservoir.update(1L);

        when(clock.wallTime()).thenReturn(5L);
        reservoir.update(2L);

        when(clock.wallTime()).thenReturn(10L);
        reservoir.update(3L);

        when(clock.wallTime()).thenReturn(15L);
        reservoir.update(4L);

        when(clock.wallTime()).thenReturn(20L);
        reservoir.update(5L);

        assertEquals(Arrays.asList(4L, 5L), reservoir.getMeasurements());
    }
}