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
import org.axonframework.eventhandling.AbstractCluster;
import org.axonframework.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Cluster implementation that publishes events to the subscribed Event Listeners asynchronously from the publishing
 * thread. This implementation can be configured to retry event when processing fails. Furthermore, a SequencingPolicy
 * will tell the cluster which Events need to be processed sequentially, and which may be processed in parallel from
 * others.
 *
 * @author Allard Buijze
 * @see SequencingPolicy
 * @see org.axonframework.unitofwork.TransactionManager
 * @since 2.0
 */
public class AsynchronousCluster extends AbstractCluster {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousCluster.class);
    private final Executor executor;
    private final ErrorHandler errorHandler;
    private final ConcurrentMap<Object, EventProcessor> currentSchedulers =
            new ConcurrentHashMap<Object, EventProcessor>();
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    // the queue on which events are places that may be processed concurrently
    private UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failed events are retried if they are not
     * explicitly non-transient with an interval of 2 seconds.
     *
     * @param identifier         The unique identifier of this cluster
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     */
    public AsynchronousCluster(String identifier, Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(identifier, executor, transactionManager, sequencingPolicy,
             new DefaultErrorHandler(RetryPolicy.retryAfter(2, TimeUnit.SECONDS)));
    }

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code> and
     * <code>sequencingPolicy</code>. Each handler will receive each event once, ignoring exceptions they may throw.
     * The Unit of Work in which Events are handled is <em>not</em> backed by any Transaction Manager.
     *
     * @param identifier       The unique identifier of this cluster
     * @param executor         The executor to process event batches with
     * @param sequencingPolicy The policy indicating which events must be processed sequentially, and which may be
     *                         executed in parallel.
     */
    public AsynchronousCluster(String identifier, Executor executor,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(identifier, executor, new DefaultUnitOfWorkFactory(), sequencingPolicy,
             new DefaultErrorHandler(RetryPolicy.proceed()));
    }

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failures are processed by the given
     * <code>errorHandler</code>.
     * <p/>
     * The Cluster is initialized with a {@link DefaultUnitOfWorkFactory}, using the given
     * <code>transactionManager</code> to manage the backing transactions.
     *
     * @param identifier         The unique identifier of this cluster
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     * @param errorHandler       The handler that handles error during event processing
     */
    public AsynchronousCluster(String identifier, Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                               ErrorHandler errorHandler) {
        this(identifier, executor, new DefaultUnitOfWorkFactory(transactionManager), sequencingPolicy, errorHandler);
    }

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>unitOfWorkFactory</code> and <code>sequencingPolicy</code>. Failures are processed by the given
     * <code>errorHandler</code>.
     * <p/>
     * If transactions are required, the given <code>unitOfWorkFactory</code> should be configured to create
     * Transaction backed Unit of Work instances.
     *
     * @param identifier        The unique identifier of this cluster
     * @param executor          The executor to process event batches with
     * @param unitOfWorkFactory The Unit of Work Factory Manager that manages Units of Work around event processing
     * @param sequencingPolicy  The policy indicating which events must be processed sequentially, and which may be
     *                          executed in parallel.
     * @param errorHandler      The handler that handles error during event processing
     */
    public AsynchronousCluster(String identifier, Executor executor, UnitOfWorkFactory unitOfWorkFactory,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                               ErrorHandler errorHandler) {
        super(identifier);
        this.errorHandler = errorHandler;
        this.executor = executor;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.sequencingPolicy = sequencingPolicy;
    }

    @Override
    public void publish(EventMessage... events) {
        for (EventMessage event : events) {
            schedule(event);
        }
    }

    /**
     * Schedules this task for execution when all pre-conditions have been met.
     *
     * @param task The task to schedule for processing.
     */
    protected void schedule(EventMessage<?> task) {
        final Object sequenceIdentifier = sequencingPolicy.getSequenceIdentifierFor(task);
        if (sequenceIdentifier == null) {
            logger.debug("Scheduling Event for full concurrent processing",
                         task.getClass().getSimpleName());
            EventProcessor scheduler = newProcessingScheduler(new NoActionCallback());
            scheduler.scheduleEvent(task);
        } else {
            logger.debug("Scheduling task of type [{}] for sequential processing in group [{}]",
                         task.getClass().getSimpleName(),
                         sequenceIdentifier.toString());
            assignEventToScheduler(task, sequenceIdentifier);
        }
    }

    private void assignEventToScheduler(EventMessage<?> task, Object sequenceIdentifier) {
        boolean taskScheduled = false;
        while (!taskScheduled) {
            EventProcessor currentScheduler = currentSchedulers.get(sequenceIdentifier);
            if (currentScheduler == null) {
                currentSchedulers.putIfAbsent(sequenceIdentifier,
                                              newProcessingScheduler(new SchedulerCleanUp(sequenceIdentifier)));
            } else {
                taskScheduled = currentScheduler.scheduleEvent(task);
                if (!taskScheduled) {
                    // we know it can be cleaned up.
                    currentSchedulers.remove(sequenceIdentifier, currentScheduler);
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
    protected EventProcessor newProcessingScheduler(
            EventProcessor.ShutdownCallback shutDownCallback) {
        logger.debug("Initializing new processing scheduler.");
        return new EventProcessor(executor, shutDownCallback, errorHandler, unitOfWorkFactory, getMembers());
    }

    private static class NoActionCallback implements EventProcessor.ShutdownCallback {

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterShutdown(EventProcessor scheduler) {
        }
    }

    private final class SchedulerCleanUp implements EventProcessor.ShutdownCallback {

        private final Object sequenceIdentifier;

        private SchedulerCleanUp(Object sequenceIdentifier) {
            this.sequenceIdentifier = sequenceIdentifier;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterShutdown(EventProcessor scheduler) {
            logger.debug("Cleaning up processing scheduler for sequence [{}]", sequenceIdentifier.toString());
            currentSchedulers.remove(sequenceIdentifier, scheduler);
        }
    }
}