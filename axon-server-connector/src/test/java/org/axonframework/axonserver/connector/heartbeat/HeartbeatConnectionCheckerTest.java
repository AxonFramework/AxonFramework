/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.utils.FakeClock;
import org.axonframework.axonserver.connector.heartbeat.connection.checker.HeartbeatConnectionChecker;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HeartbeatConnectionChecker}.
 *
 * @author Sara Pellegrini
 */
class HeartbeatConnectionCheckerTest {

    @Test
    void heartbeatNeverReceived() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        HeartbeatConnectionChecker check = new HeartbeatConnectionChecker(1_000,
                                                                      r -> {
                                                                      },
                                                                          () -> true,
                                                                          new FakeClock(instant::get));
        instant.set(instant.get().plus(60, SECONDS));
        assertTrue(check.isValid());
    }

    @Test
    void heartbeatProperlyReceived() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionChecker check = new HeartbeatConnectionChecker(1_000,
                                                                          heartbeatCallback::set,
                                                                          () -> true,
                                                                          new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(1, SECONDS));
        assertTrue(check.isValid());
    }

    @Test
    void heartbeatReceivedLate() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionChecker check = new HeartbeatConnectionChecker(1_000,
                                                                          heartbeatCallback::set,
                                                                          () -> true,
                                                                          new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(1001, MILLIS));
        assertFalse(check.isValid());
    }

    @Test
    void delegateDetectsBrokenConnection() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionChecker check = new HeartbeatConnectionChecker(1_000,
                                                                          heartbeatCallback::set,
                                                                          () -> false,
                                                                          new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(900, MILLIS));
        assertFalse(check.isValid());
    }
}