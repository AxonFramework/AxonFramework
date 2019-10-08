package org.axonframework.axonserver.connector.heartbeat;

import org.junit.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link HeartbeatMonitor}
 *
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatMonitorTest {

    @Test
    public void testConnectionAlive() {
        AtomicBoolean reconnect = new AtomicBoolean(false);
        HeartbeatMonitor monitor = new HeartbeatMonitor(() -> reconnect.set(true),
                                                        () -> true);
        monitor.run();
        assertFalse(reconnect.get());
    }

    @Test
    public void testDisconnection() {
        AtomicBoolean reconnect = new AtomicBoolean(false);
        HeartbeatMonitor monitor = new HeartbeatMonitor(() -> reconnect.set(true),
                                                        () -> false);
        monitor.run();
        assertTrue(reconnect.get());
    }
}