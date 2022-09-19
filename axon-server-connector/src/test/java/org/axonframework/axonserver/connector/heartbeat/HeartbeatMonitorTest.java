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