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

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.unitofwork.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLNonTransientException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final RetryPolicy skipFailedEvent;
    private final int maxBatchSize;
    private final int retryInterval;

    /**
     * Initialize a scheduler using the given <code>executor</code>. This scheduler uses an unbounded queue to schedule
     * events.
     *
     * @param transactionManager The transaction manager that manages underlying transactions
     * @param executor           The executor service that will process the events
     * @param shutDownCallback   The callback to notify when the scheduler finishes processing events
     * @param retryPolicy        The policy indicating how to deal with event processing failure
     * @param batchSize          The number of events to process in a single batch
     * @param retryInterval      The number of milliseconds to wait between retries
     */
    public EventProcessingScheduler(TransactionManager transactionManager, Executor executor,
                                    ShutdownCallback shutDownCallback, RetryPolicy retryPolicy, int batchSize,
                                    int retryInterval) {
        this(transactionManager, new LinkedList<T>(), executor, shutDownCallback,
             retryPolicy, batchSize, retryInterval);
    }

    /**
     * Initialize a scheduler using the given <code>executor</code>. The <code>eventQueue</code> is the queue from
     * which the scheduler should obtain it's events. This queue does not have to be thread safe, if the queue is not
     * accessed from outside the EventProcessingScheduler.
     *
     * @param transactionManager The transaction manager that manages underlying transactions
     * @param executor           The executor service that will process the events
     * @param eventQueue         The queue from which this scheduler gets events
     * @param shutDownCallback   The callback to notify when the scheduler finishes processing events
     * @param retryPolicy        The policy indicating how to deal with event processing failure
     * @param batchSize          The number of events to process in a single batch
     * @param retryInterval      The number of milliseconds to wait between retries
     */
    public EventProcessingScheduler(TransactionManager transactionManager, Queue<T> eventQueue, Executor executor,
                                    ShutdownCallback shutDownCallback, RetryPolicy retryPolicy, int batchSize,
                                    int retryInterval) {
        this.transactionManager = transactionManager;
        this.eventQueue = eventQueue;
        this.shutDownCallback = shutDownCallback;
        this.executor = executor;
        this.skipFailedEvent = retryPolicy;
        this.maxBatchSize = batchSize;
        this.retryInterval = retryInterval;
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
        final BatchStatus status = new BatchStatus(skipFailedEvent, maxBatchSize, retryInterval);
        while (mayContinue) {
            processOrRetryBatch(status);
            // Only continue processing in the current thread if yielding failed
            mayContinue = !yield();
            status.reset();
        }
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

    @SuppressWarnings("unchecked")
    private void processOrRetryBatch(BatchStatus status) {
        Object transaction = null;
        try {
            transaction = transactionManager.startTransaction();
            if (currentBatch.isEmpty()) {
                handleEventBatch(status);
            } else {
                logger.warn("Retrying {} events from the previous failed transaction.", currentBatch.size());
                retryEventBatch(status);
                logger.warn("Continuing regular processing of events.");
                handleEventBatch(status);
            }
            transactionManager.commitTransaction(transaction);
            currentBatch.clear();
        } catch (Exception e) {
            // the batch failed.
            prepareBatchRetry(status, transaction, e);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized RetryPolicy prepareBatchRetry(BatchStatus status, Object transaction, Exception e) {
        RetryPolicy policyToApply = status.getRetryPolicy();
        long retryIntervalToApply = status.getRetryInterval();
        if (policyToApply != RetryPolicy.SKIP_FAILED_EVENT && isNonTransient(e)) {
            logger.warn("RetryPolicy has been overridden. The exception {} is explicitly Non Transient. "
                                + "Failed event is skipped to prevent poison message syndrome.",
                        e.getClass().getSimpleName());
            policyToApply = RetryPolicy.SKIP_FAILED_EVENT;
        }

        if (transaction != null && policyToApply != RetryPolicy.RETRY_TRANSACTION) {
            try {
                transactionManager.commitTransaction(transaction);
            } catch (Exception transactionException) {
                logger.warn("The retry policy [{}] requires the transaction to be committed, "
                                    + "but a failure occurred while doing so. Scheduling a full retry instead.",
                            policyToApply);
                if (policyToApply == RetryPolicy.SKIP_FAILED_EVENT) {
                    logger.warn("The retry policy [{}] requires the transaction to be committed, "
                                        + "but a failure occurred while doing so. Scheduling a full retry instead, "
                                        + "excluding the failed event.", policyToApply);
                    // we take out the failed event, and retry the rest only
                    currentBatch.remove(status.getEventsProcessedInBatch());
                } else {
                    logger.warn("The retry policy [{}] requires the transaction to be committed, "
                                        + "but a failure occurred while doing so. Scheduling a full retry instead.",
                                policyToApply);
                }
                policyToApply = RetryPolicy.RETRY_TRANSACTION;
                retryIntervalToApply = 0;
            }
        }

        switch (policyToApply) {
            case RETRY_LAST_EVENT:
                this.retryAfter = System.currentTimeMillis() + retryIntervalToApply;
                for (int t = 0; t < status.getEventsProcessedInBatch(); t++) {
                    currentBatch.remove(0);
                }
                logger.warn("Transactional event processing batch failed. Rescheduling last event for retry.", e);
                break;
            case SKIP_FAILED_EVENT:
                logger.error("Transactional event processing batch failed. Ignoring failed event.", e);
                currentBatch.clear();
                break;
            case RETRY_TRANSACTION:
                if (transaction != null) {
                    try {
                        transactionManager.rollbackTransaction(transaction);
                    } catch (Exception transactionException) {
                        logger.warn("Failed rolling back a transaction.");
                    }
                }
                this.retryAfter = System.currentTimeMillis() + retryIntervalToApply;
                logger.warn("Transactional event processing batch failed. ", e);
                logger.warn("Retrying entire batch of {} events, with {} more in queue.",
                            currentBatch.size(),
                            queuedEventCount());
                break;
        }
        return status.getRetryPolicy();
    }

    private void retryEventBatch(BatchStatus status) {
        for (T event : this.currentBatch) {
            doHandle(event);
            status.recordEventProcessed();
        }
        currentBatch.clear();
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isNonTransient(Throwable exception) {
        if (exception instanceof SQLNonTransientException
                || exception instanceof AxonNonTransientException) {
            return true;
        }
        if (exception.getCause() != null && exception.getCause() != exception) {
            return isNonTransient(exception.getCause());
        }
        return false;
    }

    /**
     * Does the actual processing of the event. This method is invoked if the scheduler has decided this event is up
     * next for execution. Implementation should not pass this scheduling to an asynchronous executor
     *
     * @param event The event to handle
     */
    protected abstract void doHandle(T event);

    private void handleEventBatch(BatchStatus status) {
        T event;
        while (!status.isBatchSizeReached() && (event = nextEvent()) != null) { // NOSONAR
            doHandle(event);
            status.recordEventProcessed();
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

    /**
     * Provides details about the current status of an event handling batch.
     * <p/>
     * All instance methods in this class are meant to be used in a single thread and are therefore not thread-safe.
     * The
     * static methods are thread-safe.
     *
     * @author Allard Buijze
     * @since 0.3
     */
    private static final class BatchStatus {

        private int eventsProcessedInBatch = 0;
        private final RetryPolicy retryPolicy;
        private final int maxBatchSize;
        private final long retryInterval;

        /**
         * Initialize a BatchStatus instance with given settings.
         *
         * @param retryPolicy   The retry policy in case of processing failure
         * @param maxBatchSize  The maximum number of events to handle in a single batch
         * @param retryInterval The amount of time to wait between retries
         */
        public BatchStatus(RetryPolicy retryPolicy, int maxBatchSize, int retryInterval) {
            this.retryPolicy = retryPolicy;
            this.maxBatchSize = maxBatchSize;
            this.retryInterval = retryInterval;
        }

        /**
         * Returns the retry policy for the current transaction
         *
         * @return the retry policy for the current transaction
         */
        public RetryPolicy getRetryPolicy() {
            return retryPolicy;
        }

        /**
         * Record the fact that an event has been processed. This will increase the number of events processed in
         * current
         * transaction as well as the number of events since last yield.
         */
        protected void recordEventProcessed() {
            eventsProcessedInBatch++;
        }

        public int getEventsProcessedInBatch() {
            return eventsProcessedInBatch;
        }

        /**
         * Resets the event count for current transaction to 0 and sets the YieldPolicy to the default value
         * (YIELD_AFTER_TRANSACTION).
         */
        protected void reset() {
            eventsProcessedInBatch = 0;
        }

        /**
         * Indicates whether or not the maximum amount of events have been processed in this transaction.
         *
         * @return true if the maximum amount of events was handled, otherwise false.
         */
        protected boolean isBatchSizeReached() {
            return eventsProcessedInBatch >= maxBatchSize;
        }

        /**
         * Returns the current retry interval. This is the number of milliseconds processing should wait before
         * retrying
         * this transaction. Defaults to 5000 milliseconds.
         * <p/>
         * Note that negative values will result in immediate retries, making them practically equal to a value of 0.
         *
         * @return the current retry interval
         */
        public long getRetryInterval() {
            return retryInterval;
        }
    }
}
