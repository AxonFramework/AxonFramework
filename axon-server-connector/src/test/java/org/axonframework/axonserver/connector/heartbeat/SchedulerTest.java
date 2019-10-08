package org.axonframework.axonserver.connector.heartbeat;

import org.junit.*;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Scheduler}
 *
 * @author Sara Pellegrini
 */
public class SchedulerTest {

    @Test
    public void testScheduling() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Scheduler testSubject = new Scheduler(counter::incrementAndGet,
                                              Executors.newSingleThreadScheduledExecutor(),
                                              50,
                                              50);
        testSubject.start();
        Thread.sleep(510);
        testSubject.stop();
        assertEquals(10, counter.get());
    }

    @Test
    public void testStartSchedulingTwice() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Scheduler testSubject = new Scheduler(counter::incrementAndGet,
                                              Executors.newSingleThreadScheduledExecutor(),
                                              50,
                                              50);
        testSubject.start();
        testSubject.start();
        Thread.sleep(510);
        testSubject.stop();
        assertEquals(10, counter.get());
    }

    @Test
    public void testStopSchedulingTwice() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Scheduler testSubject = new Scheduler(counter::incrementAndGet,
                                              Executors.newSingleThreadScheduledExecutor(),
                                              50,
                                              50);
        testSubject.start();
        Thread.sleep(510);
        testSubject.stop();
        testSubject.stop();
        assertEquals(10, counter.get());
    }

    @Test
    public void testStopWhenNeverStarted() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Scheduler testSubject = new Scheduler(counter::incrementAndGet,
                                              Executors.newSingleThreadScheduledExecutor(),
                                              50,
                                              50);
        Thread.sleep(510);
        testSubject.stop();
        assertEquals(0, counter.get());
    }
}