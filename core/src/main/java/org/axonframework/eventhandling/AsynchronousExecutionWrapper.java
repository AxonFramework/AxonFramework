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
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Abstract implementation that schedules tasks for execution. This implementation allows for certain tasks to be
 * executed sequentially, while other (groups of) tasks are processed in parallel.
 *
 * @param <T> The type of object defining the task
 * @author Allard Buijze
 * @since 1.0
 */
public abstract class AsynchronousExecutionWrapper<T> {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousExecutionWrapper.class);
    private final Executor executor;
    private final ConcurrentMap<Object, EventProcessingScheduler<T>> transactions =
            new ConcurrentHashMap<Object, EventProcessingScheduler<T>>();
    private final SequencingPolicy<? super T> sequencingPolicy;
    private final BlockingQueue<T> concurrentEventQueue = new LinkedBlockingQueue<T>();
    private final TransactionManager transactionManager;

    /**
     * Initialize the AsynchronousExecutionWrapper using the given <code>executor</code> and
     * <code>transactionManager</code>. The transaction manager is used to start and stop any underlying transactions
     * necessary for task processing.
     *
     * @param transactionManager The transaction manager that will manage underlying transactions for this task
     * @param sequencingPolicy   The sequencing policy for concurrent execution of tasks
     * @param executor           The executor that processes the tasks
     */
    public AsynchronousExecutionWrapper(Executor executor, TransactionManager transactionManager,
                                        SequencingPolicy<? super T> sequencingPolicy) {
        this.executor = executor;
        this.transactionManager = transactionManager;
        this.sequencingPolicy = sequencingPolicy;
    }

    /**
     * Initialize the AsynchronousExecutionWrapper using the given <code>executor</code>.
     * <p/>
     * Note that the underlying bean will not be notified of any transactions.
     *
     * @param sequencingPolicy The sequencing policy for concurrent execution of tasks
     * @param executor         The executor that processes the tasks
     * @see #AsynchronousExecutionWrapper(java.util.concurrent.Executor, TransactionManager, SequencingPolicy)
     */
    public AsynchronousExecutionWrapper(Executor executor, SequencingPolicy<? super T> sequencingPolicy) {
        this(executor, new NoTransactionManager(), sequencingPolicy);
    }

    /**
     * Does the actual processing of the task. This method is invoked if the scheduler has decided this task is up next
     * for execution. Implementation should not pass this scheduling to an asynchronous executor.
     *
     * @param task The task to handle
     */
    protected abstract void doHandle(T task);

    /**
     * Schedules this task for execution when all pre-conditions have been met.
     *
     * @param task The task to schedule for processing.
     */
    protected void schedule(T task) {
        final Object sequenceIdentifier = sequencingPolicy.getSequenceIdentifierFor(task);
        if (sequenceIdentifier == null) {
            logger.debug("Scheduling task of type [{}] for full concurrent processing",
                         task.getClass().getSimpleName());
            EventProcessingScheduler<T> scheduler = newProcessingScheduler(new NoActionCallback(),
                                                                           this.concurrentEventQueue);
            scheduler.scheduleEvent(task);
        } else {
            logger.debug("Scheduling task of type [{}] for sequential processing in group [{}]",
                         task.getClass().getSimpleName(),
                         sequenceIdentifier.toString());
            assignEventToScheduler(task, sequenceIdentifier);
        }
    }

    private void assignEventToScheduler(T task, Object sequenceIdentifier) {
        boolean taskScheduled = false;
        while (!taskScheduled) {
            EventProcessingScheduler<T> currentScheduler = transactions.get(sequenceIdentifier);
            if (currentScheduler == null) {
                transactions.putIfAbsent(sequenceIdentifier,
                                         newProcessingScheduler(new TransactionCleanUp(sequenceIdentifier)));
            } else {
                taskScheduled = currentScheduler.scheduleEvent(task);
                if (!taskScheduled) {
                    // we know it can be cleaned up.
                    transactions.remove(sequenceIdentifier, currentScheduler);
                }
            }
        }
    }

    /**
     * Creates a new scheduler instance that schedules tasks on the executor service for the managed EventListener.
     *
     * @param shutDownCallback The callback that needs to be notified when the scheduler stops processing.
     * @return a new scheduler instance
     */
    protected EventProcessingScheduler<T> newProcessingScheduler(
            EventProcessingScheduler.ShutdownCallback shutDownCallback) {
        logger.debug("Initializing new processing scheduler.");
        return newProcessingScheduler(shutDownCallback, new LinkedList<T>());
    }

    /**
     * Creates a new scheduler instance schedules tasks on the executor service for the managed EventListener. The
     * Scheduler must get tasks from the given <code>taskQueue</code>.
     *
     * @param shutDownCallback The callback that needs to be notified when the scheduler stops processing.
     * @param taskQueue        The queue from which this scheduler should store and get tasks
     * @return a new scheduler instance
     */
    protected EventProcessingScheduler<T> newProcessingScheduler(
            EventProcessingScheduler.ShutdownCallback shutDownCallback,
            Queue<T> taskQueue) {
        return new EventProcessingScheduler<T>(transactionManager, taskQueue, executor, shutDownCallback) {
            @Override
            protected void doHandle(T task) {
                AsynchronousExecutionWrapper.this.doHandle(task);
            }
        };
    }

    private static class NoActionCallback implements EventProcessingScheduler.ShutdownCallback {

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterShutdown(EventProcessingScheduler scheduler) {
        }
    }

    private final class TransactionCleanUp implements EventProcessingScheduler.ShutdownCallback {

        private final Object sequenceIdentifier;

        private TransactionCleanUp(Object sequenceIdentifier) {
            this.sequenceIdentifier = sequenceIdentifier;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterShutdown(EventProcessingScheduler scheduler) {
            logger.debug("Cleaning up processing scheduler for sequence [{}]", sequenceIdentifier.toString());
            transactions.remove(sequenceIdentifier, scheduler);
        }
    }
}
