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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.axonframework.core.eventhandler.YieldPolicy.DO_NOT_YIELD;

/**
 * The EventProcessingScheduler is responsible for scheduling all events within the same SequencingIdentifier in an
 * ExecutorService. It will only handle events that were present in the queue at the moment processing started. Any
 * events added later will be rescheduled automatically.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class EventProcessingScheduler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessingScheduler.class);

    private final EventListener eventListener;
    private final ShutdownCallback shutDownCallback;
    private final TransactionAware transactionListener;
    private final ExecutorService executorService;

    // guarded by "this"
    private final Queue<Event> events = new LinkedList<Event>();
    // guarded by "this"
    private boolean isScheduled = false;
    private boolean cleanedUp;
    private final List<Event> currentBatch = new LinkedList<Event>();

    /**
     * Initialize a scheduler for the given <code>eventListener</code> using the given <code>executorService</code>.
     *
     * @param eventListener    The event listener for which this scheduler schedules events
     * @param executorService  The executor service that will process the events
     * @param shutDownCallback The callback to notify when the scheduler finishes processing events
     */
    public EventProcessingScheduler(EventListener eventListener, ExecutorService executorService,
                                    ShutdownCallback shutDownCallback) {
        this.eventListener = eventListener;
        this.shutDownCallback = shutDownCallback;
        if (eventListener instanceof TransactionAware) {
            this.transactionListener = (TransactionAware) eventListener;
        } else {
            this.transactionListener = new TransactionIgnoreAdapter();
        }
        this.executorService = executorService;
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
    protected synchronized Event nextEvent() {
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
    protected synchronized boolean yield() {
        if (events.size() > 0 || currentBatch.size() > 0) {
            isScheduled = true;
            try {
                executorService.submit((Runnable) this);
            }
            catch (RejectedExecutionException e) {
                return false;
            }
        } else {
            cleanUp();
        }
        return true;
    }

    /**
     * Will look at the current scheduling status and schedule an EventHandlerInvokerTask if none is already active.
     * <p/>
     * This method is thread safe
     */
    protected synchronized void scheduleIfNecessary() {
        if (!isScheduled) {
            isScheduled = true;
            executorService.submit(this);
        }
    }

    /**
     * Returns the number of events currently queued for processing.
     *
     * @return the number of events currently queued for processing.
     */
    protected synchronized int queuedEventCount() {
        return events.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        boolean mayContinue = true;
        final TransactionStatusImpl status = new TransactionStatusImpl(queuedEventCount());
        TransactionStatus.set(status);
        while (mayContinue) {
            try {
                transactionListener.beforeTransaction(status);
                if (currentBatch.isEmpty()) {
                    handleEventBatch(status);
                } else {
                    retryEventBatch(status);
                    handleEventBatch(status);
                }
                transactionListener.afterTransaction(status);
                currentBatch.clear();
            }
            catch (Exception e) {
                // the batch failed.
                status.markFailed(e);
                tryAfterTransactionCall(status);
                switch (status.getRetryPolicy()) {
                    case RETRY_LAST_EVENT:
                        markLastEventForRetry();
                        break;
                    case IGNORE_FAILED_TRANSACTION:
                        currentBatch.clear();
                        break;
                }
            }
            mayContinue = (queuedEventCount() > 0 && DO_NOT_YIELD.equals(status.getYieldPolicy())) || !yield();
            status.resetTransactionStatus();
        }
        TransactionStatus.clear();
    }

    private void tryAfterTransactionCall(TransactionStatusImpl status) {
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

    protected synchronized void cleanUp() {
        isScheduled = false;
        cleanedUp = true;
        shutDownCallback.afterShutdown(this);
    }

    private static class TransactionStatusImpl extends TransactionStatus {

        public TransactionStatusImpl(int transactionSize) {
            setMaxTransactionSize(transactionSize);
        }
    }

    private static class TransactionIgnoreAdapter implements TransactionAware {

        /**
         * {@inheritDoc}
         */
        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
            transactionStatus.setRetryPolicy(RetryPolicy.RETRY_LAST_EVENT);
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
