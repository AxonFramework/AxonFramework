package org.axonframework.axonserver.connector.heartbeat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Responsible to schedule generic tasks at a fixed rate.
 * The scheduling can be started an stopped at any time.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);

    private static final long DEFAULT_INITIAL_DELAY = 10_000;

    private static final long DEFAULT_RATE = 1_000;

    private final ScheduledExecutorService scheduler;

    private final long initialDelay;

    private final long rate;

    private final AtomicReference<ScheduledFuture> scheduledTask = new AtomicReference<>();

    private final Runnable task;

    /**
     * Constructs an instance of a {@link Scheduler} using a default initial delay of 10 seconds,
     * a default scheduling period of 1 second and a single thread {@link ScheduledExecutorService}.
     *
     * @param task the task to be scheduled
     */
    public Scheduler(Runnable task) {
        this(task, Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Constructs an instance of a {@link Scheduler} using a default initial delay of 10 seconds
     * and a default scheduling period of 1 second.
     * @param task the task to be scheduled
     * @param scheduler the {@link ScheduledExecutorService} to use for scheduling the task
     */
    public Scheduler(Runnable task, ScheduledExecutorService scheduler) {
        this(task, scheduler, DEFAULT_INITIAL_DELAY, DEFAULT_RATE);
    }

    /**
     * Primary constructor for {@link Scheduler}
     * @param task the task to be scheduled
     * @param scheduler the {@link ScheduledExecutorService} to use for scheduling the task
     * @param initialDelay the initial delay, in milliseconds
     * @param rate the scheduling period, in milliseconds
     */
    public Scheduler(Runnable task,
                     ScheduledExecutorService scheduler,
                     long initialDelay,
                     long rate) {
        this.task = task;
        this.scheduler = scheduler;
        this.initialDelay = initialDelay;
        this.rate = rate;
    }

    /**
     * Schedule the specified task accordingly with the specified scheduling setting.
     */
    public void start() {
        ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(task,
                                                                           initialDelay,
                                                                           rate,
                                                                           MILLISECONDS);
        if (!scheduledTask.compareAndSet(null, scheduledFuture)) {
            scheduledFuture.cancel(true);
            LOGGER.warn("{} already scheduled.", task.getClass().getSimpleName());
        }
    }

    /**
     * Interrupt the scheduled task if running and stop all the following scheduled executions.
     */
    public void stop() {
        ScheduledFuture<?> scheduledFuture = scheduledTask.get();
        if (scheduledFuture != null && scheduledTask.compareAndSet(scheduledFuture, null)) {
            scheduledFuture.cancel(true);
        } else {
            LOGGER.warn("{} already stopped.", task.getClass().getSimpleName());
        }
    }
}
