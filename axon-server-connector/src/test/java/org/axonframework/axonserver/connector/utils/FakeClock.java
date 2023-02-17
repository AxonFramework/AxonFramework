/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.axonserver.connector.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * Fake implementation of {@link Clock} used for testing purpose.
 * It provides the desired {@link Instant} invoking the specified supplier.
 *
 * @author Sara Pellegrini
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

    public FakeClock plusMillis(long millis) {
        return new FakeClock(() -> instant.get().plusMillis(millis), zone);
    }
}
