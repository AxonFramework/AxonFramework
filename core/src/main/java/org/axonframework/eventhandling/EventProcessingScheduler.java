/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.YieldPolicy.DO_NOT_YIELD;

/**
 * Scheduler that keeps track of (Event processing) tasks that need to be executed sequentially.
 *
 * @param <T> The type of class representing the processing instruction for the event.
 * @author Allard Buijze
 * @since 1.0
 */
public abstract class EventProcessingScheduler<T> implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessingScheduler.class);

    private final ShutdownCallback shutDownCallback;
    private final TransactionManager transactionManager;
    private final Executor executor;
    // guarded by "this"
    private final Queue<T> eventQueue;
    private final List<T> currentBatch = new LinkedList<T>();
    // guarded by "this"
    private boolean isScheduled = false;
    private volatile boolean cleanedUp;
    private volatile long retryAfter;
    private volatile boolean transactionStarted;

    /**
     * Initialize a scheduler using the given <code>executor</code>. This scheduler uses an unbounded queue to schedule
     * events.
     *
     * @param transactionManager The transaction manager that manages underlying transactions
     * @param executor           The executor service that will process the events
     * @param shutDownCallback   The callback to notify when the scheduler finishes processing events
     */
    public EventProcessingScheduler(TransactionManager transactionManager, Executor executor,
                                    ShutdownCallback shutDownCallback) {
        this(transactionManager, new LinkedList<T>(), executor, shutDownCallback);
    }

    /**
     * Initialize a scheduler using the given <code>executor</code>. The <code>eventQueue</code> is the queue from
     * which
     * the scheduler should obtain it's events. This queue must be thread safe, as it can be used simultaneously by
     * multiple threads.
     *
     * @param transactionManager The transaction manager that manages underlying transactions
     * @param executor           The executor service that will process the events
     * @param eventQueue         The queue from which this scheduler gets events
     * @param shutDownCallback   The callback to notify when the scheduler finishes processing events
     */
    public EventProcessingScheduler(TransactionManager transactionManager, Queue<T> eventQueue, Executor executor,
                                    ShutdownCallback shutDownCallback) {
        this.transactionManager = transactionManager;
        this.eventQueue = eventQueue;
        this.shutDownCallback = shutDownCallback;
        this.executor = executor;
    }

    /**
     * Schedules an event for processing. Will schedule a new invoker task if none is currently active.
     * <p/>
     * If the current scheduler is in the process of being shut down, this method will return false.
     * <p/>
     * This method is thread safe
     *
     * @param event the event to schedule
     * @return true if the event was scheduled successfully, false if this scheduler is not available to process events
     *
     * @throws IllegalStateException if the queue in this scheduler does not have the capacity to add this event
     */
    public synchronized boolean scheduleEvent(T event) {
        if (cleanedUp) {
            // this scheduler has been shut down; accept no more events
            return false;
        }
        // add the event to the queue which this scheduler processes
        eventQueue.add(event);
        scheduleIfNecessary();
        return true;
    }

    /**
     * Returns the next event in the queue, if available. If returns false if no further events are available for
     * processing. In that case, it will also set the scheduled status to false.
     * <p/>
     * This method is thread safe
     *
     * @return the next DomainEvent for processing, of null if none is available
     */
    private synchronized T nextEvent() {
        T e = eventQueue.poll();
        if (e != null) {
            currentBatch.add(e);
        }
        return e;
    }

    /**
     * Tries to yield to other threads by rescheduling processing of any further queued events. If rescheduling fails,
     * this call returns false, indicating that processing should continue in the current thread.
     * <p/>
     * This method is thread safe
     *
     * @return true if yielding succeeded, false otherwise.
     */
    private synchronized boolean yield() {
        if (eventQueue.size() > 0 || currentBatch.size() > 0) {
            try {
                if (retryAfter <= System.currentTimeMillis()) {
                    executor.execute(this);
                    logger.debug("Processing of event listener yielded.");
                } else {
                    long waitTimeRemaining = retryAfter - System.currentTimeMillis();
                    boolean executionScheduled = scheduleDelayedExecution(waitTimeRemaining);
                    if (!executionScheduled) {
                        logger.warn("The provided executor does not seem to support delayed execution. Scheduling for "
                                            + "immediate processing and expecting processing to wait "
                                            + "if scheduled to soon.");
                        executor.execute(this);
                    }
                }
            } catch (RejectedExecutionException e) {
                logger.info("Processing of event listener could not yield. Executor refused the task.");
                return false;
            }
        } else {
            cleanUp();
        }
        return true;
    }

    private boolean scheduleDelayedExecution(long waitTimeRemaining) {
        if (executor instanceof ScheduledExecutorService) {
            logger.debug("Executor supports delayed executing. Rescheduling for processing in {} millis",
                         waitTimeRemaining);
            ((ScheduledExecutorService) executor).schedule(this, waitTimeRemaining, TimeUnit.MILLISECONDS);
            return true;
        }
        return false;
    }

    /**
     * Will look at the current scheduling status and schedule an EventHandlerInvokerTask if none is already active.
     * <p/>
     * This method is thread safe
     */
    private synchronized void scheduleIfNecessary() {
        if (!isScheduled) {
            isScheduled = true;
            executor.execute(this);
        }
    }

    /**
     * Returns the number of events currently queued for processing.
     *
     * @return the number of events currently queued for processing.
     */
    private synchronized int queuedEventCount() {
        return eventQueue.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        boolean mayContinue = true;
        waitUntilAllowedStartingTime();
        final TransactionStatus status = new TransactionStatus();
        status.setMaxTransactionSize(queuedEventCount() + currentBatch.size());
        TransactionStatus.set(status);
        while (mayContinue) {
            processOrRetryBatch(status);
            boolean inRetryMode =
                    !status.isSuccessful() && status.getRetryPolicy() != RetryPolicy.SKIP_FAILED_EVENT;
            /*
             * Only continue processing in the current thread if:
             * - all of the following
             *   - not in retry mode
             *   - there are events waiting in the queue
             *   - the yielding policy is DO_NOT_YIELD
             * - or
             *   - yielding failed because the executor rejected the execution
             */
            mayContinue = (!inRetryMode && queuedEventCount() > 0 && DO_NOT_YIELD.equals(status.getYieldPolicy()))
                    || !yield();
            status.resetTransactionStatus();
        }
        TransactionStatus.clear();
    }

    private void waitUntilAllowedStartingTime() {
        long waitTimeRemaining = retryAfter - System.currentTimeMillis();
        if (waitTimeRemaining > 0) {
            try {
                logger.warn("Event processing started before delay expired. Forcing thread to sleep for {} millis.",
                            waitTimeRemaining);
                Thread.sleep(waitTimeRemaining);
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted while waiting for retry. Scheduling for immediate retry.");
                Thread.currentThread().interrupt();
            } finally {
                retryAfter = 0;
            }
        }
    }

    private void processOrRetryBatch(TransactionStatus status) {
        try {
            this.transactionStarted = false;
            if (currentBatch.isEmpty()) {
                handleEventBatch(status);
            } else {
                logger.warn("Retrying {} events from the previous failed transaction.", currentBatch.size());
                retryEventBatch(status);
                logger.warn("Continuing regular processing of events.");
                handleEventBatch(status);
            }
            if (transactionStarted) {
                transactionManager.afterTransaction(status);
            }
            currentBatch.clear();
        } catch (Exception e) {
            // the batch failed.
            prepareBatchRetry(status, e);
        }
    }

    private synchronized void prepareBatchRetry(TransactionStatus status, Exception e) {
        status.markFailed(e);
        tryAfterTransactionCall(status);
        switch (status.getRetryPolicy()) {
            case RETRY_LAST_EVENT:
                markLastEventForRetry();
                logger.warn("Transactional event processing batch failed. Rescheduling last event for retry.", e);
                break;
            case SKIP_FAILED_EVENT:
                logger.error("Transactional event processing batch failed. Ignoring failed event.", e);
                currentBatch.clear();
                status.setRetryInterval(0);
                break;
            case RETRY_TRANSACTION:
                logger.warn("Transactional event processing batch failed. ", e);
                logger.warn("Retrying entire batch of {} events, with {} more in queue.",
                            currentBatch.size(),
                            queuedEventCount());
                break;
        }
        this.retryAfter = System.currentTimeMillis() + status.getRetryInterval();
    }

    private void tryAfterTransactionCall(TransactionStatus status) {
        try {
            transactionManager.afterTransaction(status);
        } catch (Exception e) {
            logger.warn("Call to afterTransaction method of failed transaction resulted in an exception.", e);
        }
        transactionStarted = false;
    }

    private void markLastEventForRetry() {
        T lastEvent = currentBatch.get(currentBatch.size() - 1);
        currentBatch.clear();
        if (lastEvent != null) {
            currentBatch.add(lastEvent);
        }
    }

    private void retryEventBatch(TransactionStatus status) {
        for (T event : this.currentBatch) {
            startTransactionIfNecessary(status);
            doHandle(event);
            status.recordEventProcessed();
        }
        currentBatch.clear();
    }

    /**
     * Does the actual processing of the event. This method is invoked if the scheduler has decided this event is up
     * next for execution. Implementation should not pass this scheduling to an asynchronous executor
     *
     * @param event The event to handle
     */
    protected abstract void doHandle(T event);

    private void handleEventBatch(TransactionStatus status) {
        T event;
        while (!status.isTransactionSizeReached() && (event = nextEvent()) != null) { // NOSONAR
            startTransactionIfNecessary(status);
            doHandle(event);
            status.recordEventProcessed();
        }
    }

    private void startTransactionIfNecessary(TransactionStatus status) {
        if (!transactionStarted) {
            transactionManager.beforeTransaction(status);
            transactionStarted = true;
        }
    }

    private synchronized void cleanUp() {
        isScheduled = false;
        cleanedUp = true;
        shutDownCallback.afterShutdown(this);
    }

    /**
     * Callback that allows the SequenceManager to receive a notification when this scheduler finishes processing
     * events.
     */
    interface ShutdownCallback {

        /**
         * Called when event processing is complete. This means that there are no more events waiting and the last
         * transactional batch has been committed successfully.
         *
         * @param scheduler the scheduler that completed processing.
         */
        void afterShutdown(EventProcessingScheduler scheduler);
    }
}
