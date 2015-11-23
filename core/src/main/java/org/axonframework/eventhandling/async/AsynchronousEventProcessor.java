/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionManager;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * EventProcessor implementation that publishes events to the subscribed Event Listeners asynchronously from the publishing
 * thread. This implementation can be configured to retry event publication when processing fails. Furthermore, a SequencingPolicy
 * will tell the event processor which Events need to be processed sequentially, and which may be processed in parallel from
 * others.
 *
 * @author Allard Buijze
 * @see SequencingPolicy
 * @see TransactionManager
 * @since 2.0
 */
public class AsynchronousEventProcessor extends AbstractEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousEventProcessor.class);
    private final Executor executor;
    private final ErrorHandler errorHandler;
    private final ConcurrentMap<Object, EventProcessorTask> currentTasks = new ConcurrentHashMap<>();
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Creates an AsynchronousEventProcessor implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failed events are retried if they are not
     * explicitly non-transient with an interval of 2 seconds.
     *
     * @param identifier         The unique identifier of this event processor
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     */
    public AsynchronousEventProcessor(String identifier, Executor executor, TransactionManager transactionManager,
                                      SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(identifier, executor, transactionManager, sequencingPolicy,
             new DefaultErrorHandler(RetryPolicy.retryAfter(2, TimeUnit.SECONDS)));
    }

    /**
     * Creates an AsynchronousEventProcessor implementation using the given <code>executor</code> and
     * <code>sequencingPolicy</code>. Each handler will receive each event once, ignoring exceptions they may throw.
     * The Unit of Work in which Events are handled is <em>not</em> backed by any Transaction Manager.
     *
     * @param identifier       The unique identifier of this event processor
     * @param executor         The executor to process event batches with
     * @param sequencingPolicy The policy indicating which events must be processed sequentially, and which may be
     *                         executed in parallel.
     */
    public AsynchronousEventProcessor(String identifier, Executor executor,
                                      SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(identifier, executor, new DefaultUnitOfWorkFactory(), sequencingPolicy,
             new DefaultErrorHandler(RetryPolicy.proceed()));
    }

    /**
     * Creates an AsynchronousEventProcessor implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failures are processed by the given
     * <code>errorHandler</code>.
     * <p/>
     * The EventProcessor is initialized with a {@link DefaultUnitOfWorkFactory}, using the given
     * <code>transactionManager</code> to manage the backing transactions.
     *
     * @param identifier         The unique identifier of this event processor
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     * @param errorHandler       The handler that handles error during event processing
     */
    public AsynchronousEventProcessor(String identifier, Executor executor, TransactionManager transactionManager,
                                      SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                                      ErrorHandler errorHandler) {
        this(identifier, executor, new DefaultUnitOfWorkFactory(transactionManager), sequencingPolicy, errorHandler);
    }

    /**
     * Creates an AsynchronousEventProcessor implementation using the given <code>executor</code>,
     * <code>unitOfWorkFactory</code> and <code>sequencingPolicy</code>. Failures are processed by the given
     * <code>errorHandler</code>.
     * <p/>
     * If transactions are required, the given <code>unitOfWorkFactory</code> should be configured to create
     * Transaction backed Unit of Work instances.
     *
     * @param name              The unique identifier of this event processor
     * @param executor          The executor to process event batches with
     * @param unitOfWorkFactory The Unit of Work Factory Manager that manages Units of Work around event processing
     * @param sequencingPolicy  The policy indicating which events must be processed sequentially, and which may be
     *                          executed in parallel.
     * @param errorHandler      The handler that handles error during event processing
     */
    public AsynchronousEventProcessor(String name, Executor executor, UnitOfWorkFactory unitOfWorkFactory,
                                      SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                                      ErrorHandler errorHandler) {
        super(name);
        Assert.notNull(errorHandler, "errorHandler may not be null");
        Assert.notNull(unitOfWorkFactory, "unitOfWorkFactory may not be null");
        Assert.notNull(sequencingPolicy, "sequencingPolicy may not be null");
        this.errorHandler = errorHandler;
        this.executor = executor;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.sequencingPolicy = sequencingPolicy;
    }

    /**
     * Creates an AsynchronousEventProcessor implementation using the given <code>executor</code>,
     * <code>unitOfWorkFactory</code> and <code>sequencingPolicy</code>. Failures are processed by the given
     * <code>errorHandler</code>. Event Listeners are invoked in the order provided by the <code>orderResolver</code>.
     * <p/>
     * If transactions are required, the given <code>unitOfWorkFactory</code> should be configured to create
     * Transaction backed Unit of Work instances.
     * <p/>
     * Event Listeners with the lowest order are invoked first.
     *
     * @param name              The unique identifier of this event processor
     * @param executor          The executor to process event batches with
     * @param unitOfWorkFactory The Unit of Work Factory Manager that manages Units of Work around event processing
     * @param sequencingPolicy  The policy indicating which events must be processed sequentially, and which may be
     *                          executed in parallel.
     * @param errorHandler      The handler that handles error during event processing
     * @param orderResolver     The resolver providing the expected order of the listeners
     */
    public AsynchronousEventProcessor(String name, Executor executor, UnitOfWorkFactory unitOfWorkFactory,
                                      SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                                      ErrorHandler errorHandler, OrderResolver orderResolver) {
        super(name, new EventListenerOrderComparator(orderResolver));
        Assert.notNull(errorHandler, "errorHandler may not be null");
        Assert.notNull(unitOfWorkFactory, "unitOfWorkFactory may not be null");
        Assert.notNull(sequencingPolicy, "sequencingPolicy may not be null");
        this.errorHandler = errorHandler;
        this.executor = executor;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.sequencingPolicy = sequencingPolicy;
    }

    @Override
    protected void doPublish(final List<EventMessage<?>> events, Set<EventListener> eventListeners,
                             Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors,
                             final MultiplexingEventProcessingMonitor eventProcessingMonitor) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().afterCommit(u -> {
                for (EventMessage event : events) {
                    schedule(event, eventProcessingMonitor, eventListeners, interceptors);
                }
            });
        } else {
            for (EventMessage event : events) {
                schedule(event, eventProcessingMonitor, eventListeners, interceptors);
            }
        }
    }

    /**
     * Schedules this task for execution when all pre-conditions have been met.
     * @param event                 The message to schedule for processing.
     * @param eventProcessingMonitor  The monitor to invoke after completion
     * @param eventListeners          Immutable real-time view on subscribed event listeners
     * @param interceptors            Registered interceptors that need to intercept each event before it's handled
     */
    protected void schedule(EventMessage<?> event, MultiplexingEventProcessingMonitor eventProcessingMonitor,
                            Set<EventListener> eventListeners,
                            Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors) {
        final Object sequenceIdentifier = sequencingPolicy.getSequenceIdentifierFor(event);
        if (sequenceIdentifier == null) {
            logger.debug("Scheduling Event for full concurrent processing {}",
                         event.getClass().getSimpleName());
            EventProcessorTask scheduler = newProcessingScheduler(new NoActionCallback(), eventListeners,
                                                                  eventProcessingMonitor, interceptors);
            scheduler.scheduleEvent(event);
        } else {
            logger.debug("Scheduling task of type [{}] for sequential processing in group [{}]",
                         event.getClass().getSimpleName(),
                         sequenceIdentifier.toString());
            assignEventToScheduler(event, sequenceIdentifier, eventProcessingMonitor, eventListeners, interceptors);
        }
    }

    private void assignEventToScheduler(EventMessage<?> task, Object sequenceIdentifier,
                                        MultiplexingEventProcessingMonitor eventProcessingMonitor,
                                        Set<EventListener> eventListeners,
                                        Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors) {
        boolean taskScheduled = false;
        while (!taskScheduled) {
            EventProcessorTask currentScheduler = currentTasks.get(sequenceIdentifier);
            if (currentScheduler == null) {
                currentTasks.putIfAbsent(sequenceIdentifier,
                                              newProcessingScheduler(new SchedulerCleanUp(sequenceIdentifier),
                                                                     eventListeners, eventProcessingMonitor,
                                                                     interceptors));
            } else {
                taskScheduled = currentScheduler.scheduleEvent(task);
                if (!taskScheduled) {
                    // we know it can be cleaned up.
                    currentTasks.remove(sequenceIdentifier, currentScheduler);
                }
            }
        }
    }

    /**
     * Creates a new scheduler instance that schedules tasks on the executor service for the managed EventListener.
     *
     * @param shutDownCallback       The callback that needs to be notified when the scheduler stops processing.
     * @param eventListeners         The listeners to process the event with
     * @param eventProcessingMonitor The monitor to invoke after completion
     * @param interceptors           Registered interceptors that need to intercept each event before it's handled
     * @return a new scheduler instance
     */
    protected EventProcessorTask newProcessingScheduler(
            EventProcessorTask.ShutdownCallback shutDownCallback, Set<EventListener> eventListeners,
            MultiplexingEventProcessingMonitor eventProcessingMonitor,
            Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors) {
        logger.debug("Initializing new processing scheduler.");
        return new EventProcessorTask(executor,
                                  shutDownCallback,
                                  errorHandler,
                                  unitOfWorkFactory,
                                  eventListeners,
                                  eventProcessingMonitor,
                                  interceptors);
    }

    private static class NoActionCallback implements EventProcessorTask.ShutdownCallback {

        @Override
        public void afterShutdown(EventProcessorTask scheduler) {
        }
    }

    private final class SchedulerCleanUp implements EventProcessorTask.ShutdownCallback {

        private final Object sequenceIdentifier;

        private SchedulerCleanUp(Object sequenceIdentifier) {
            this.sequenceIdentifier = sequenceIdentifier;
        }

        @Override
        public void afterShutdown(EventProcessorTask scheduler) {
            logger.debug("Cleaning up processing scheduler for sequence [{}]", sequenceIdentifier.toString());
            currentTasks.remove(sequenceIdentifier, scheduler);
        }
    }
}
