package org.axonframework.axonserver.connector.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatScheduler {

    private final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final ScheduledExecutorService scheduler;

    private final HeartbeatSource heartbeatSource;

    private final long initialDelay;

    private final long rate;

    private final AtomicReference<ScheduledFuture> scheduledTask = new AtomicReference<>();

    public HeartbeatScheduler(HeartbeatSource heartbeatSource) {
        this(heartbeatSource, Executors.newScheduledThreadPool(1));
    }

    public HeartbeatScheduler(HeartbeatSource heartbeatSource, ScheduledExecutorService scheduler) {
        this(heartbeatSource, scheduler, 10_000, 1000);
    }

    public HeartbeatScheduler(HeartbeatSource heartbeatSource, ScheduledExecutorService scheduler,
                              long initialDelay,
                              long rate) {
        this.scheduler = scheduler;
        this.heartbeatSource = heartbeatSource;
        this.initialDelay = initialDelay;
        this.rate = rate;
    }

    public void start() {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(this::send, initialDelay, rate, MILLISECONDS);
        if (!scheduledTask.compareAndSet(null, task)) {
            task.cancel(true);
            log.warn("Heartbeat already scheduled.");
        }
    }

    public void stop() {
        ScheduledFuture<?> scheduled = scheduledTask.get();
        if (scheduled != null && scheduledTask.compareAndSet(scheduled, null)) {
            scheduled.cancel(true);
        } else {
            log.warn("Heartbeat already stopped.");
        }
    }

    private void send() {
        try {
            heartbeatSource.send();
        } catch (Exception e) {
            log.warn("Problem sending heartbeat to AxonServer.", e);
        }
    }
}
