/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.micrometer.reservoir;

import io.micrometer.core.instrument.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A reservoir of measurements constrained by a sliding window that stores only the measurements made
 * in the last {@code N} seconds (or other time unit).
 * <p>
 * Adapted from com.codahale.metrics.SlidingTimeWindowReservoir from io.dropwizard.metrics:metrics-core:3.1.2
 *
 * @author Volker Fritzsch
 * @author Coda Hale
 * @author ceetav
 * @author Marijn van Zelst
 * @since 4.1
 */
public class SlidingTimeWindowReservoir {

    // allow for this many duplicate ticks before overwriting measurements
    private static final int COLLISION_BUFFER = 256;
    // only trim on updating once every N
    private static final int TRIM_THRESHOLD = 256;

    private final Clock clock;
    private final ConcurrentSkipListMap<Long, Long> measurements;
    private final long window;
    private final AtomicLong lastTick;
    private final AtomicLong count;

    /**
     * Creates a new {@link SlidingTimeWindowReservoir} with the given clock and window of time.
     *
     * @param window     the window of time
     * @param windowUnit the unit of {@code window}
     * @param clock      the {@link Clock} to use
     */
    public SlidingTimeWindowReservoir(long window, TimeUnit windowUnit, Clock clock) {
        this.clock = clock;
        this.measurements = new ConcurrentSkipListMap<>();
        this.window = windowUnit.toNanos(window) * COLLISION_BUFFER;
        this.lastTick = new AtomicLong(clock.monotonicTime() * COLLISION_BUFFER);
        this.count = new AtomicLong();
    }

    /**
     * Add new measurement value
     *
     * @param value the measurement value
     */
    public void update(long value) {
        if (count.incrementAndGet() % TRIM_THRESHOLD == 0) {
            trim();
        }
        measurements.put(getTick(), value);
    }

    /**
     * Retrieve the measurements
     *
     * @return the measurements
     */
    public List<Long> getMeasurements() {
        trim();
        return new ArrayList<>(measurements.values());
    }

    private long getTick() {
        for (; ; ) {
            final long oldTick = lastTick.get();
            final long tick = clock.monotonicTime() * COLLISION_BUFFER;
            // ensure the tick is strictly incrementing even if there are duplicate ticks
            final long newTick = tick - oldTick > 0 ? tick : oldTick + 1;
            if (lastTick.compareAndSet(oldTick, newTick)) {
                return newTick;
            }
        }
    }

    private void trim() {
        measurements.headMap(getTick() - window).clear();
    }
}
