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

package org.axonframework.saga.annotation;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionManager;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.saga.*;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * A SagaManager implementation that processes Sagas asynchronously. Incoming events are placed on a queue and
 * processed by a given number of processors. Modified saga state is persisted in batches to the repository, to
 * minimize communication overhead with back-ends.
 * <p/>
 * This SagaManager implementation guarantees a "happens before" type processing for each Saga. That means that the
 * behavior of asynchronously processed events is exactly identical as the behavior if the events were processed
 * completely sequentially.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AsyncAnnotatedSagaManager implements SagaManager, EventProcessingMonitorSupport {

    private static final WaitStrategy DEFAULT_WAIT_STRATEGY = new BlockingWaitStrategy();
    private WaitStrategy waitStrategy = DEFAULT_WAIT_STRATEGY;
    private static final int DEFAULT_BUFFER_SIZE = 512;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private static final int DEFAULT_PROCESSOR_COUNT = 1;
    private int processorCount = DEFAULT_PROCESSOR_COUNT;
    private final EventBus eventBus;
    private final Class<? extends AbstractAnnotatedSaga>[] sagaTypes;
    private final ParameterResolverFactory parameterResolverFactory;
    private final SagaManagerStatus sagaManagerStatus = new SagaManagerStatus();
    private final EventProcessingMonitorCollection processingMonitors = new EventProcessingMonitorCollection();
    private volatile Disruptor<AsyncSagaProcessingEvent> disruptor;
    private boolean shutdownExecutorOnStop = true;
    private Executor executor = Executors.newCachedThreadPool();
    private SagaRepository sagaRepository = new InMemorySagaRepository();
    private volatile SagaFactory sagaFactory = new GenericSagaFactory();
    private UnitOfWorkFactory unitOfWorkFactory = new DefaultUnitOfWorkFactory();
    private long startTimeout = 5000;

    /**
     * Initializes an Asynchronous Saga Manager using default values for the given <code>sagaTypes</code>.
     * <p/>
     * After initialization, the SagaManager must be explicitly started using the {@link #start()} method.
     *
     * @param sagaTypes The types of Saga this saga manager will process incoming events for
     */
    @SafeVarargs
    public AsyncAnnotatedSagaManager(Class<? extends AbstractAnnotatedSaga>... sagaTypes) {
        this(ClasspathParameterResolverFactory.forClass(
                     sagaTypes.length == 0 ? AsyncAnnotatedSagaManager.class : sagaTypes[0]),
             sagaTypes);
    }

    /**
     * Initializes an Asynchronous Saga Manager using default values for the given <code>sagaTypes</code>.
     * <p/>
     * After initialization, the SagaManager must be explicitly started using the {@link #start()} method.
     *
     * @param parameterResolverFactory The parameter resolver factory to resolve parameters of annotated handlers
     * @param sagaTypes                The types of Saga this saga manager will process incoming events for
     */
    @SafeVarargs
    public AsyncAnnotatedSagaManager(ParameterResolverFactory parameterResolverFactory,
                                     Class<? extends AbstractAnnotatedSaga>... sagaTypes) {
        this.parameterResolverFactory = parameterResolverFactory;
        this.eventBus = null;
        this.sagaTypes = Arrays.copyOf(sagaTypes, sagaTypes.length);
    }

    /**
     * Starts the Saga Manager by starting the processor threads and subscribes it with the <code>eventBus</code>. If
     * the saga manager is already started, it is only re-subscribed to the event bus.
     */
    public synchronized void start() {
        if (disruptor == null) {
            sagaManagerStatus.setStatus(true);
            disruptor = new Disruptor<>(new AsyncSagaProcessingEvent.Factory(),
                                        bufferSize,
                                        new ValidatingExecutor(executor, startTimeout),
                                        ProducerType.MULTI,
                                        waitStrategy);
            disruptor.handleExceptionsWith(new LoggingExceptionHandler());
            disruptor.handleEventsWith(AsyncSagaEventProcessor.createInstances(sagaRepository, parameterResolverFactory,
                                                                               unitOfWorkFactory, processorCount,
                                                                               disruptor.getRingBuffer(),
                                                                               sagaManagerStatus))
                     .then(new MonitorNotifier(processingMonitors));
            disruptor.start();
        }
    }

    /**
     * Unsubscribes this Saga Manager from the event bus and stops accepting new events. The method is blocked until
     * all scheduled events have been processed. Note that any manually provided Executors using ({@link
     * #setExecutor(java.util.concurrent.Executor)} are not shut down.
     * <p/>
     * If the Saga Manager was already stopped, nothing happens.
     */
    public synchronized void stop() {
        sagaManagerStatus.setStatus(false);
        if (disruptor != null) {
            disruptor.shutdown();
            if (shutdownExecutorOnStop && executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }
        }
        disruptor = null;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void handle(final EventMessage event) {
        if (disruptor != null) {
            for (final Class<? extends AbstractAnnotatedSaga> sagaType : sagaTypes) {
                SagaMethodMessageHandlerInspector inspector =
                        SagaMethodMessageHandlerInspector.getInstance(sagaType, parameterResolverFactory);
                final List<SagaMethodMessageHandler> handlers = inspector.getMessageHandlers(event);
                if (!handlers.isEmpty()) {
                    AbstractAnnotatedSaga newSagaInstance = null;
                    for (SagaMethodMessageHandler handler : handlers) {
                        if (newSagaInstance == null && handler.getCreationPolicy() != SagaCreationPolicy.NONE) {
                            newSagaInstance = (AbstractAnnotatedSaga) sagaFactory.createSaga(inspector
                                                                                                     .getSagaType());
                        }
                    }
                    disruptor.publishEvent(new SagaProcessingEventTranslator(event, inspector, handlers,
                                                                             newSagaInstance));
                }
            }
        }
    }

    @Override
    public Class<?> getTargetType() {
        return sagaTypes.length > 0 ? sagaTypes[0] : Void.TYPE;
    }

    @Override
    public Registration subscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        return processingMonitors.subscribeEventProcessingMonitor(monitor);
    }

    /**
     * Sets the executor that provides the threads for the processors. Note that you must ensure that this executor
     * is capable of delivering <em>all</em> of the required threads at once. If that is not the case, the Saga Manager
     * might hang while waiting for the executor to provide them. Must be set <em>before</em> the SagaManager is
     * started.
     * <p/>
     * By default, a thread is created for each processor.
     *
     * @param executor the executor that provides the threads for the processors
     * @see #setProcessorCount(int)
     */
    public synchronized void setExecutor(Executor executor) {
        Assert.state(disruptor == null, "Cannot set executor after SagaManager has started");
        this.shutdownExecutorOnStop = false;
        this.executor = executor;
    }

    /**
     * Sets the saga repository to store and load Sagas from. Must be set <em>before</em> the SagaManager is
     * started.
     * <p/>
     * Defaults to an in-memory repository.
     *
     * @param sagaRepository the saga repository to store and load Sagas from
     */
    public synchronized void setSagaRepository(SagaRepository sagaRepository) {
        Assert.state(disruptor == null, "Cannot set sagaRepository when SagaManager has started");
        this.sagaRepository = sagaRepository;
    }

    /**
     * Sets the SagaFactory responsible for creating new Saga instances when required. Must be set <em>before</em> the
     * SagaManager is started.
     * <p/>
     * Defaults to a {@link GenericSagaFactory} instance.
     *
     * @param sagaFactory the SagaFactory responsible for creating new Saga instances
     */
    public synchronized void setSagaFactory(SagaFactory sagaFactory) {
        Assert.state(disruptor == null, "Cannot set sagaFactory when SagaManager has started");
        this.sagaFactory = sagaFactory;
    }

    /**
     * Sets the TransactionManager used to manage any transactions required by the underlying storage mechanism. Note
     * that batch sizes set by this transaction manager are ignored. Must be set <em>before</em> the
     * SagaManager is started.
     * <p/>
     * By default, no transactions are managed.
     *
     * @param transactionManager the TransactionManager used to manage any transactions required by the underlying
     *                           storage mechanism.
     */
    public synchronized void setTransactionManager(TransactionManager transactionManager) {
        Assert.state(disruptor == null, "Cannot set transactionManager when SagaManager has started");
        this.unitOfWorkFactory = new DefaultUnitOfWorkFactory(transactionManager);
    }

    /**
     * Sets the number of processors (threads) to process events with. Ensure that the given {@link
     * #setExecutor(java.util.concurrent.Executor) executor} is capable of processing this amount of concurrent tasks.
     * Must be set <em>before</em> the SagaManager is started.
     * <p/>
     * Defaults to 1.
     *
     * @param processorCount the number of processors (threads) to process events with
     */
    public synchronized void setProcessorCount(int processorCount) {
        Assert.state(disruptor == null, "Cannot set processorCount when SagaManager has started");
        this.processorCount = processorCount;
    }

    /**
     * Sets the amount of time (in milliseconds) the AsyncSagaManager will wait for the async processors to be assigned
     * a thread from the executor. This is used to ensure that the executor provides a thread for the processors,
     * instead of queueing them. This typically occurs when using a thread pool with a core pool size smaller than the
     * processorCount.
     * <p/>
     * Must be set before calling {@link #start()}. Defaults to 5000 (5 seconds).
     *
     * @param startTimeout the number of millis to wait for the processor to have been assigned a thread. Defaults to
     *                     5000 (5 seconds).
     */
    public synchronized void setStartTimeout(long startTimeout) {
        this.startTimeout = startTimeout;
    }

    /**
     * Sets the size of the processing buffer. This is equal to the amount of events that may awaiting for processing
     * before the input is blocked. Must be set <em>before</em> the SagaManager is started.
     * <p/>
     * Note that this value <em>must</em> be a power of 2.
     * <p/>
     * Defaults to 512.
     *
     * @param bufferSize The size of the processing buffer. Must be a power of 2.
     */
    public synchronized void setBufferSize(int bufferSize) {
        Assert.isTrue(Integer.bitCount(bufferSize) == 1, "The buffer size must be a power of 2");
        Assert.state(disruptor == null, "Cannot set bufferSize when SagaManager has started");
        this.bufferSize = bufferSize;
    }

    /**
     * Sets the WaitStrategy to use when event processors need to wait for incoming events.
     * <p/>
     * Defaults to a BlockingWaitStrategy.
     *
     * @param waitStrategy the WaitStrategy to use when event processors need to wait for incoming events
     */
    public synchronized void setWaitStrategy(WaitStrategy waitStrategy) {
        Assert.state(disruptor == null, "Cannot set waitStrategy when SagaManager has started");
        this.waitStrategy = waitStrategy;
    }

    private static final class SagaProcessingEventTranslator implements EventTranslator<AsyncSagaProcessingEvent> {

        private final EventMessage event;
        private final SagaMethodMessageHandlerInspector annotationInspector;
        private final List<SagaMethodMessageHandler> handlers;
        private final AbstractAnnotatedSaga newSagaInstance;

        private SagaProcessingEventTranslator(EventMessage event, SagaMethodMessageHandlerInspector annotationInspector,
                                              List<SagaMethodMessageHandler> handlers,
                                              AbstractAnnotatedSaga newSagaInstance) {
            this.event = event;
            this.annotationInspector = annotationInspector;
            this.handlers = handlers;
            this.newSagaInstance = newSagaInstance;
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public void translateTo(AsyncSagaProcessingEvent entry, long sequence) {
            entry.reset(event, annotationInspector.getSagaType(), handlers, newSagaInstance);
        }
    }

    private static final class LoggingExceptionHandler implements ExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(LoggingExceptionHandler.class);

        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            logger.warn("A fatal exception occurred while processing an Event for a Saga. "
                                + "Processing will continue with the next Event", ex);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            logger.warn("An exception occurred while starting the AsyncAnnotatedSagaManager.", ex);
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            logger.warn("An exception occurred while shutting down the AsyncAnnotatedSagaManager.", ex);
        }
    }

    /**
     * Exposes the running state of the SagaManager.
     */
    static class SagaManagerStatus {

        private volatile boolean isRunning;

        private void setStatus(boolean running) {
            isRunning = running;
        }

        /**
         * Indicates whether the SagaManager that provided this instance is (still) running.
         *
         * @return <code>true</code> if the SagaManager is running, otherwise <code>false</code>
         */
        public boolean isRunning() {
            return isRunning;
        }
    }

    /**
     * Executor wrapper that checks whether a given task is executed immediately.
     */
    private static class ValidatingExecutor implements Executor {

        private final Executor delegate;
        private final long timeoutMillis;

        /**
         * Initialize the Executor that delegates to the given <code>executor</code> and wait for at most
         * <code>timeoutMillis</code> for tasks to actually start.
         *
         * @param executor      The executor expected to provide a thread for execution
         * @param timeoutMillis The maximum amount of time to wait for the thread to pick up the task
         */
        public ValidatingExecutor(Executor executor, long timeoutMillis) {
            this.delegate = executor;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void execute(Runnable command) {
            final StartDetectingRunnable task = new StartDetectingRunnable(command);
            delegate.execute(task);
            try {
                if (!task.awaitStarted(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    throw new AxonConfigurationException("It seems that the given Executor is not providing a thread "
                                                                 + "for the AsyncSagaManager. Ensure that the "
                                                                 + "corePoolSize is larger than the processor count.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Runnable implementation that exposes the fact it has started executing.
     */
    private static class StartDetectingRunnable implements Runnable {

        private final Runnable delegate;
        private final CountDownLatch cdl = new CountDownLatch(1);

        /**
         * @param command The actual runnable to execute
         */
        public StartDetectingRunnable(Runnable command) {
            this.delegate = command;
        }

        @Override
        public void run() {
            cdl.countDown();
            delegate.run();
        }

        /**
         * Indicates whether the task has started, potentially waiting for the given <code>timeout</code>.
         *
         * @param timeout The amount of time to wait for the process to start
         * @param unit    The unit of time
         * @return whether the task has started or not
         *
         * @throws InterruptedException when the thread receives an interrupt while waiting for the start notification
         */
        public boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return cdl.await(timeout, unit);
        }
    }

    private static class MonitorNotifier implements EventHandler<AsyncSagaProcessingEvent> {

        private final EventProcessingMonitor monitor;
        private final List<EventMessage> processedMessages = new ArrayList<>();

        public MonitorNotifier(EventProcessingMonitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void onEvent(AsyncSagaProcessingEvent event, long sequence, boolean endOfBatch) throws Exception {
            processedMessages.add(event.getPublishedEvent());
            if (endOfBatch) {
                monitor.onEventProcessingCompleted(processedMessages);
                processedMessages.clear();
            }
        }
    }
}
