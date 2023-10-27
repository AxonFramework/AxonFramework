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

import org.axonframework.axonserver.connector.utils.FakeScheduler;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HeartbeatMonitor}
 *
 * @author Sara Pellegrini
 */
class HeartbeatMonitorTest {

    @Test
    void connectionAlive() {
        FakeScheduler fakeScheduler = new FakeScheduler();
        AtomicBoolean reconnect = new AtomicBoolean(false);
        HeartbeatMonitor monitor = new HeartbeatMonitor(() -> reconnect.set(true),
                                                        () -> true,
                                                        fakeScheduler,
                                                        1000,
                                                        100);
        monitor.start();
        fakeScheduler.timeElapses(1450);
        assertFalse(reconnect.get());
        monitor.shutdown();
        fakeScheduler.timeElapses(500);
        assertEquals(5, fakeScheduler.performedExecutionsCount());
    }

    @Test
    void disconnection() {
        FakeScheduler fakeScheduler = new FakeScheduler();
        AtomicBoolean reconnect = new AtomicBoolean(false);
        HeartbeatMonitor monitor = new HeartbeatMonitor(() -> reconnect.set(true),
                                                        () -> false,
                                                        fakeScheduler,
                                                        1000,
                                                        100);
        monitor.start();
        fakeScheduler.timeElapses(1450);
        assertTrue(reconnect.get());
        assertEquals(5, fakeScheduler.performedExecutionsCount());
        fakeScheduler.timeElapses(500);
        assertEquals(10, fakeScheduler.performedExecutionsCount());
        monitor.shutdown();
        fakeScheduler.timeElapses(500);
        assertEquals(10, fakeScheduler.performedExecutionsCount());
    }
}