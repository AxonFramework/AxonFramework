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

package org.axonframework.eventhandling.async;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler that keeps track of (Event processing) tasks that need to be executed sequentially.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class EventProcessor implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);

    private final ShutdownCallback shutDownCallback;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final Executor executor;
    private final ErrorHandler errorHandler;
    // guarded by "this"
    private final Deque<EventMessage<?>> eventQueue;
    // guarded by "this"
    private boolean isScheduled = false;
    private volatile boolean cleanedUp;
    private List<EventListener> listeners;
    private volatile long retryAfter = 0;

    /**
     * Initialize a scheduler using the given <code>executor</code>. This scheduler uses an unbounded queue to schedule
     * events.
     *
     * @param executor          The executor service that will process the events
     * @param shutDownCallback  The callback to notify when the scheduler finishes processing events
     * @param errorHandler      The error handler to invoke when an error occurs while committing a Unit of Work
     * @param unitOfWorkFactory The factory providing instances of the Unit of Work
     * @param eventListeners    The event listeners that should handle incoming events
     */
    public EventProcessor(Executor executor, ShutdownCallback shutDownCallback, ErrorHandler errorHandler,
                          UnitOfWorkFactory unitOfWorkFactory, Collection<EventListener> eventListeners) {
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.eventQueue = new LinkedList<EventMessage<?>>();
        this.shutDownCallback = shutDownCallback;
        this.executor = executor;
        this.errorHandler = errorHandler;
        this.listeners = new ArrayList<EventListener>(eventListeners);
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
    public synchronized boolean scheduleEvent(EventMessage<?> event) {
        if (cleanedUp) {
            // this scheduler has been shut down; accept no more events
            return false;
        }
        // add the event to the queue which this scheduler processes
        eventQueue.add(event);
        if (!isScheduled) {
            isScheduled = true;
            executor.execute(this);
        }
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
    private synchronized EventMessage<?> nextEvent() {
        return eventQueue.poll();
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
        if (eventQueue.isEmpty()) {
            cleanUp();
        } else {
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
        }
        return true;
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
     * {@inheritDoc}
     */
    @Override
    public void run() {
        boolean mayContinue = true;
        waitUntilAllowedStartingTime();
        int itemsAtStart = eventQueue.size();
        int processedItems = 0;
        while (mayContinue) {
            RetryPolicy result = processNextEntry();
            processedItems++;
            // Continue processing if there is no rescheduling involved and there are events in the queue, or if yielding failed
            mayContinue = (processedItems < itemsAtStart
                    && !eventQueue.isEmpty()
                    && !result.requiresRescheduleEvent())
                    || !yield();
        }
    }

    @SuppressWarnings("unchecked")
    private RetryPolicy processNextEntry() {
        final EventMessage<?> event = nextEvent();
        RetryPolicy policy = RetryPolicy.proceed();
        if (event != null) {
            UnitOfWork uow = null;
            try {
                uow = unitOfWorkFactory.createUnitOfWork();
                policy = doHandle(event);
                if (policy.requiresRollback()) {
                    uow.rollback();
                } else {
                    uow.commit();
                }
                if (policy.requiresRescheduleEvent()) {
                    eventQueue.addFirst(event);
                }
                retryAfter = System.currentTimeMillis() + policy.waitTime();
            } catch (RuntimeException e) {
                policy = errorHandler.handleError(e, event, null);
                if (policy.requiresRescheduleEvent()) {
                    eventQueue.addFirst(event);
                    retryAfter = System.currentTimeMillis() + policy.waitTime();
                }
                // the batch failed.
                if (uow != null && uow.isStarted()) {
                    uow.rollback();
                }
            }
        }
        return policy;
    }

    /**
     * Does the actual processing of the event. This method is invoked if the scheduler has decided this event is up
     * next for execution. Implementation should not pass this scheduling to an asynchronous executor
     *
     * @param event The event to handle
     * @return the policy for retrying/proceeding with this event
     */
    protected RetryPolicy doHandle(EventMessage<?> event) {
        for (EventListener member : listeners) {
            try {
                member.handle(event);
            } catch (RuntimeException e) {
                RetryPolicy policy = errorHandler.handleError(e, event, member);
                if (policy.requiresRescheduleEvent() || policy.requiresRollback()) {
                    return policy;
                }
            }
        }
        return RetryPolicy.proceed();
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
        void afterShutdown(EventProcessor scheduler);
    }
}
