/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler;

import org.axonframework.core.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.axonframework.core.eventhandler.YieldPolicy.DO_NOT_YIELD;

/**
 * The EventProcessingScheduler is responsible for scheduling all events within the same SequencingIdentifier in an
 * Executor. It will only handle events that were present in the queue at the moment processing started. Any events
 * added later will be rescheduled automatically.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class EventProcessingScheduler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessingScheduler.class);

    private final EventListener eventListener;
    private final ShutdownCallback shutDownCallback;
    private final TransactionAware transactionListener;
    private final Executor executor;

    // guarded by "this"
    private final Queue<Event> events = new LinkedList<Event>();
    // guarded by "this"
    private boolean isScheduled = false;
    private boolean cleanedUp;
    private final List<Event> currentBatch = new LinkedList<Event>();
    private long retryAfter;

    /**
     * Initialize a scheduler for the given <code>eventListener</code> using the given <code>executor</code>.
     *
     * @param eventListener    The event listener for which this scheduler schedules events
     * @param executor         The executor service that will process the events
     * @param shutDownCallback The callback to notify when the scheduler finishes processing events
     */
    public EventProcessingScheduler(EventListener eventListener, Executor executor,
                                    ShutdownCallback shutDownCallback) {
        this.eventListener = eventListener;
        this.shutDownCallback = shutDownCallback;
        if (eventListener instanceof TransactionAware) {
            this.transactionListener = (TransactionAware) eventListener;
        } else {
            this.transactionListener = new TransactionIgnoreAdapter();
        }
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
     */
    public synchronized boolean scheduleEvent(Event event) {
        if (cleanedUp) {
            return false;
        }
        events.add(event);
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
    private synchronized Event nextEvent() {
        return events.poll();
    }

    /**
     * Tries to yield to other threads be rescheduling processing of any further queued events. If rescheduling fails,
     * this call returns false, indicating that processing should continue in the current thread.
     * <p/>
     * This method is thread safe
     *
     * @return true if yielding succeeded, false otherwise.
     */
    private synchronized boolean yield() {
        if (events.size() > 0 || currentBatch.size() > 0) {
            isScheduled = true;
            try {
                if (retryAfter <= System.currentTimeMillis()) {
                    executor.execute(this);
                    logger.info("Processing of event listener [{}] yielded.", eventListener.toString());
                } else {
                    long waitTimeRemaining = retryAfter - System.currentTimeMillis();
                    boolean executionScheduled = scheduleDelayedExecution(waitTimeRemaining);
                    if (!executionScheduled) {
                        logger.warn("The provided executor does not seem to support delayed execution. Scheduling for "
                                + "immediate processing and expecting processing to wait if scheduled to soon.");
                        executor.execute(this);
                    }
                }
            }
            catch (RejectedExecutionException e) {
                logger.info("Processing of event listener [{}] could not yield. Executor refused the task.",
                            eventListener.toString());
                waitUntilAllowedStartingTime();
                return false;
            }
        } else {
            cleanUp();
        }
        return true;
    }

    private boolean scheduleDelayedExecution(long waitTimeRemaining) {
        if (executor instanceof ScheduledExecutorService) {
            logger.info("Executor supports delayed executing. Rescheduling for processing in {} millis",
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
        return events.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        boolean mayContinue = true;
        waitUntilAllowedStartingTime();
        final TransactionStatus status = new TransactionStatus();
        status.setMaxTransactionSize(queuedEventCount());
        TransactionStatus.set(status);
        while (mayContinue) {
            processOrRetryBatch(status);
            boolean inRetryMode =
                    !status.isSuccessful() && status.getRetryPolicy() != RetryPolicy.IGNORE_FAILED_TRANSACTION;
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

    private synchronized void waitUntilAllowedStartingTime() {
        long waitTimeRemaining = retryAfter - System.currentTimeMillis();
        if (waitTimeRemaining > 0) {
            try {
                logger.warn("Event processing started before delay expired. Forcing thread to sleep for {} millis.",
                            waitTimeRemaining);
                Thread.sleep(waitTimeRemaining);
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted while waiting for retry. Scheduling for immediate retry.");
            } finally {
                retryAfter = 0;
            }
        }
    }

    private void processOrRetryBatch(TransactionStatus status) {
        try {
            transactionListener.beforeTransaction(status);
            if (currentBatch.isEmpty()) {
                handleEventBatch(status);
            } else {
                logger.info("Retrying {} events from the previous failed transaction.", currentBatch.size());
                retryEventBatch(status);
                logger.info("Continuing processing of next events.");
                handleEventBatch(status);
            }
            transactionListener.afterTransaction(status);
            currentBatch.clear();
        }
        catch (Exception e) {
            // the batch failed.
            prepareBatchRetry(status, e);
        }
    }

    private synchronized void prepareBatchRetry(TransactionStatus status, Exception e) {
        status.markFailed(e);
        tryAfterTransactionCall(status);
        switch (status.getRetryPolicy()) {
            case RETRY_LAST_EVENT:
                logger.warn("Transactional event processing batch failed. Rescheduling last event for retry.");
                markLastEventForRetry();
                break;
            case IGNORE_FAILED_TRANSACTION:
                logger.warn("Transactional event processing batch failed. Ignoring failed events.");
                currentBatch.clear();
                status.setRetryInterval(0);
                break;
            case RETRY_TRANSACTION:
                logger.warn("Transactional event processing batch failed. Retrying entire batch.");
                break;
        }
        this.retryAfter = System.currentTimeMillis() + status.getRetryInterval();
    }

    private void tryAfterTransactionCall(TransactionStatus status) {
        try {
            transactionListener.afterTransaction(status);
        } catch (Exception e) {
            logger.warn("Call to afterTransaction method of failed transaction resulted in an exception.", e);
        }
    }

    private void markLastEventForRetry() {
        Event lastEvent = currentBatch.get(currentBatch.size() - 1);
        currentBatch.clear();
        if (lastEvent != null) {
            currentBatch.add(lastEvent);
        }
    }

    private void retryEventBatch(TransactionStatus status) {
        for (Event event : this.currentBatch) {
            eventListener.handle(event);
            status.recordEventProcessed();
        }
        currentBatch.clear();
    }

    private void handleEventBatch(TransactionStatus status) {
        Event event;
        while (!status.isTransactionSizeReached() && (event = nextEvent()) != null) {
            currentBatch.add(event);
            eventListener.handle(event);
            status.recordEventProcessed();
        }
    }

    private synchronized void cleanUp() {
        isScheduled = false;
        cleanedUp = true;
        shutDownCallback.afterShutdown(this);
    }

    private static class TransactionIgnoreAdapter implements TransactionAware {

        /**
         * {@inheritDoc}
         */
        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterTransaction(TransactionStatus transactionStatus) {
        }
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
