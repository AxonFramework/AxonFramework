package org.axonframework.axonserver.connector.heartbeat;

import org.junit.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link HeartbeatConnectionCheck}.
 *
 * @author Sara Pellegrini
 */
public class HeartbeatConnectionCheckTest {

    @Test
    public void testHeartbeatNeverReceived() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        HeartbeatConnectionCheck check = new HeartbeatConnectionCheck(1_000,
                                                                      r -> {
                                                                      },
                                                                      () -> true,
                                                                      new FakeClock(instant::get));
        instant.set(instant.get().plus(60, SECONDS));
        assertTrue(check.isValid());
    }

    @Test
    public void testHeartbeatProperlyReceived() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionCheck check = new HeartbeatConnectionCheck(1_000,
                                                                      heartbeatCallback::set,
                                                                      () -> true,
                                                                      new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(1, SECONDS));
        assertTrue(check.isValid());
    }

    @Test
    public void testHeartbeatReceivedLate() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionCheck check = new HeartbeatConnectionCheck(1_000,
                                                                      heartbeatCallback::set,
                                                                      () -> true,
                                                                      new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(1001, MILLIS));
        assertFalse(check.isValid());
    }

    @Test
    public void testDelegateDetectsBrokenConnection() {
        AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        AtomicReference<Runnable> heartbeatCallback = new AtomicReference<>();
        HeartbeatConnectionCheck check = new HeartbeatConnectionCheck(1_000,
                                                                      heartbeatCallback::set,
                                                                      () -> false,
                                                                      new FakeClock(instant::get));
        heartbeatCallback.get().run();
        instant.set(instant.get().plus(900, MILLIS));
        assertFalse(check.isValid());
    }
}