/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.async;

import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Scheduler that keeps track of (Event processing) tasks that need to be executed sequentially.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class EventProcessorTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessorTask.class);

    private final ShutdownCallback shutDownCallback;
    private final Executor executor;
    private final Deque<ProcessingTask> taskQueue;
    private boolean isScheduled = false;
    private volatile boolean cleanedUp;

    private final Object runnerMonitor = new Object();

    /**
     * Initialize a scheduler using the given {@code executor}. This scheduler uses an unbounded queue to schedule
     * events.
     *
     * @param executor         The executor service that will process the events
     * @param shutDownCallback The callback to notify when the scheduler finishes processing events
     */
    public EventProcessorTask(Executor executor, ShutdownCallback shutDownCallback) {
        this.taskQueue = new LinkedList<>();
        this.shutDownCallback = shutDownCallback;
        this.executor = executor;
    }

    /**
     * Schedules a batch of events for processing. Will schedule a new invoker task if none is currently active.
     * <p/>
     * If the current scheduler is in the process of being shut down, this method will return false.
     * <p/>
     * This method is thread safe.
     *
     * @param events    the events to schedule
     * @param processor the component that will do the actual processing of the events
     * @return true if the event was scheduled successfully, false if this scheduler is not available to process events
     * @throws IllegalStateException if the queue in this scheduler does not have the capacity to add this event
     */
    public synchronized boolean scheduleEvents(List<? extends EventMessage<?>> events,
                                               Consumer<List<? extends EventMessage<?>>> processor) {
        if (cleanedUp) {
            // this scheduler has been shut down; accept no more events
            return false;
        }
        // add the task to the queue which this scheduler processes
        taskQueue.add(new ProcessingTask(events, processor));
        if (!isScheduled) {
            isScheduled = true;
            executor.execute(this);
        }
        return true;
    }

    @Override
    public void run() {
        synchronized (runnerMonitor) {
            boolean mayContinue = true;
            int itemsAtStart = taskQueue.size();
            int processedItems = 0;
            while (mayContinue) {
                processNextTask();
                processedItems++;
                // Continue processing if there is no rescheduling involved and there are events in the queue, or if yielding failed
                mayContinue = (processedItems < itemsAtStart && !taskQueue.isEmpty()) || !yieldProcessing();
            }
        }
    }

    private void processNextTask() {
        ProcessingTask task = nextTask();
        task.processor.accept(task.events);
    }

    private synchronized ProcessingTask nextTask() {
        return taskQueue.poll();
    }

    /**
     * Tries to yield to other threads by rescheduling processing of any further queued events. If rescheduling fails,
     * this call returns false, indicating that processing should continue in the current thread.
     * <p/>
     * This method is thread safe
     *
     * @return true if yielding succeeded, false otherwise.
     */
    private synchronized boolean yieldProcessing() {
        if (taskQueue.isEmpty()) {
            cleanUp();
        } else {
            try {
                executor.execute(this);
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing of event listener yielded.");
                }
            } catch (RejectedExecutionException e) {
                logger.info("Processing of event listener could not yield. Executor refused the task.");
                return false;
            }
        }
        return true;
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
    @FunctionalInterface
    public interface ShutdownCallback {

        /**
         * Called when event processing is complete. This means that there are no more events waiting and the last
         * transactional batch has been committed successfully.
         *
         * @param scheduler the scheduler that completed processing.
         */
        void afterShutdown(EventProcessorTask scheduler);
    }

    private static class ProcessingTask {
        private final List<? extends EventMessage<?>> events;
        private final Consumer<List<? extends EventMessage<?>>> processor;

        public ProcessingTask(List<? extends EventMessage<?>> events,
                              Consumer<List<? extends EventMessage<?>>> processor) {
            this.events = events;
            this.processor = processor;
        }
    }
}
