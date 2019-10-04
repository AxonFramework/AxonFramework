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
public class Scheduler {

    private final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final ScheduledExecutorService scheduler;

    private final long initialDelay;

    private final long rate;

    private final AtomicReference<ScheduledFuture> scheduledTask = new AtomicReference<>();

    private final Runnable runnable;

    public Scheduler(Runnable runnable) {
        this(runnable, Executors.newScheduledThreadPool(1));
    }

    public Scheduler(Runnable runnable, ScheduledExecutorService scheduler) {
        this(runnable, scheduler, 10_000, 1000);
    }

    public Scheduler(Runnable runnable,
                     ScheduledExecutorService scheduler,
                     long initialDelay,
                     long rate) {
        this.runnable = runnable;
        this.scheduler = scheduler;
        this.initialDelay = initialDelay;
        this.rate = rate;
    }

    public void start() {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(runnable,
                                                                initialDelay,
                                                                rate,
                                                                MILLISECONDS);
        if (!scheduledTask.compareAndSet(null, task)) {
            task.cancel(true);
            log.warn("{} already scheduled.", runnable.getClass().getSimpleName());
        }
    }

    public void stop() {
        ScheduledFuture<?> task = scheduledTask.get();
        if (task != null && scheduledTask.compareAndSet(task, null)) {
            task.cancel(true);
        } else {
            log.warn("{} already stopped.", runnable.getClass().getSimpleName());
        }
    }
}
