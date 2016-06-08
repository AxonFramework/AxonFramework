/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.Registration;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.lang.String.format;

/**
 * Asynchronous CommandBus implementation with very high performance characteristics. It divides the command handling
 * process in two steps, which can be executed in different threads. The CommandBus is backed by a {@link Disruptor},
 * which ensures that two steps are executed sequentially in these threads, while minimizing locking and inter-thread
 * communication.
 * <p>
 * The process is split into two separate steps, each of which is executed in a different thread:
 * <ol>
 * <li><em>Command Handler execution</em><br/>This process invokes the command handler with the incoming command. The
 * result and changes to the aggregate are recorded for the next step.</li>
 * <li><em>Event storage and publication</em><br/>This process stores all generated domain events and publishes them
 * (with any optional application events) to the event bus. Finally, an asynchronous task is scheduled to invoke the
 * command handler callback with the result of the command handling result.</li>
 * </ol>
 * <p>
 * <em>Exceptions and recovery</em>
 * <p>
 * This separation of process steps makes this implementation very efficient and highly performing. However, it does
 * not cope with exceptions very well. When an exception occurs, an Aggregate that has been loaded is potentially
 * corrupt. That means that an aggregate does not represent a state that can be reproduced by replaying its committed
 * events. Although this implementation will recover from this corrupt state, it may result in a number of commands
 * being rejected in the meantime. These command may be retried if the cause of the {@link
 * AggregateStateCorruptedException} does not indicate a non-transient error.
 * <p>
 * Commands that have been executed against a potentially corrupt Aggregate will result in a {@link
 * AggregateStateCorruptedException} exception. These commands are automatically rescheduled for processing by
 * default. Use {@link DisruptorConfiguration#setRescheduleCommandsOnCorruptState(boolean)} disable this feature. Note
 * that the order in which commands are executed is not fully guaranteed when this feature is enabled (default).
 * <p>
 * <em>Limitations of this implementation</em>
 * <p>
 * Although this implementation allows applications to achieve extreme performance (over 1M commands on commodity
 * hardware), it does have some limitations. It only allows a single aggregate to be invoked during command processing.
 * <p>
 * This implementation can only work with Event Sourced Aggregates.
 * <p>
 * <em>Infrastructure considerations</em>
 * <p>
 * This CommandBus implementation has special requirements for the Repositories being used during Command Processing.
 * Therefore, the Repository instance to use in the Command Handler must be created using {@link
 * #createRepository(org.axonframework.eventsourcing.AggregateFactory)}.
 * Using another repository will most likely result in undefined behavior.
 * <p>
 * The DisruptorCommandBus must have access to at least 3 threads, two of which are permanently used while the
 * DisruptorCommandBus is operational. At least one additional thread is required to invoke callbacks and initiate a
 * recovery process in the case of exceptions.
 * <p>
 * Consider providing an alternative {@link IdentifierFactory} implementation. The default
 * implementation used {@link java.util.UUID#randomUUID()} to generated identifier for Events. The poor performance of
 * this method severely impacts overall performance of the DisruptorCommandBus. A better performing alternative is, for
 * example, <a href="http://johannburkard.de/software/uuid/" target="_blank"><code>com.eaio.uuid.UUID</code></a>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(DisruptorCommandBus.class);
    private static final ThreadGroup DISRUPTOR_THREAD_GROUP = new ThreadGroup("DisruptorCommandBus");

    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> commandHandlers = new ConcurrentHashMap<>();
    private final Disruptor<CommandHandlingEntry> disruptor;
    private final CommandHandlerInvoker[] commandHandlerInvokers;
    private final List<MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors;
    private final List<MessageHandlerInterceptor<CommandMessage<?>>> invokerInterceptors;
    private final List<MessageHandlerInterceptor<CommandMessage<?>>> publisherInterceptors;
    private final ExecutorService executorService;
    private final boolean rescheduleOnCorruptState;
    private final long coolingDownPeriod;
    private final CommandTargetResolver commandTargetResolver;
    private final int publisherCount;
    private volatile boolean started = true;
    private volatile boolean disruptorShutDown = false;

    /**
     * Initialize the DisruptorCommandBus with given resources, using default configuration settings. Uses a Blocking
     * WaitStrategy on a RingBuffer of size 4096. The (2) Threads required for command execution are created
     * immediately. Additional threads are used to invoke response callbacks and to initialize a recovery process in
     * the case of errors.
     *
     * @param eventStore The EventStore where generated events must be stored
     */
    public DisruptorCommandBus(EventStore eventStore) {
        this(eventStore, new DisruptorConfiguration());
    }

    /**
     * Initialize the DisruptorCommandBus with given resources and settings. The Threads required for command
     * execution are immediately requested from the Configuration's Executor, if any. Otherwise, they are created.
     *
     * @param eventStore    The EventStore where generated events must be stored
     * @param configuration The configuration for the command bus
     */
    @SuppressWarnings("unchecked")
    public DisruptorCommandBus(EventStore eventStore, DisruptorConfiguration configuration) {
        Assert.notNull(eventStore, "eventStore may not be null");
        Assert.notNull(configuration, "configuration may not be null");
        Executor executor = configuration.getExecutor();
        if (executor == null) {
            executorService = Executors.newCachedThreadPool(new AxonThreadFactory(DISRUPTOR_THREAD_GROUP));
            executor = executorService;
        } else {
            executorService = null;
        }
        rescheduleOnCorruptState = configuration.getRescheduleCommandsOnCorruptState();
        invokerInterceptors = new ArrayList<>(configuration.getInvokerInterceptors());
        publisherInterceptors = new ArrayList<>(configuration.getPublisherInterceptors());
        dispatchInterceptors = new ArrayList<>(configuration.getDispatchInterceptors());
        TransactionManager transactionManager = configuration.getTransactionManager();
        disruptor = new Disruptor<>(CommandHandlingEntry::new, configuration.getBufferSize(), executor,
                                    configuration.getProducerType(), configuration.getWaitStrategy());
        commandTargetResolver = configuration.getCommandTargetResolver();

        // configure invoker Threads
        commandHandlerInvokers = initializeInvokerThreads(eventStore, configuration);
        // configure publisher Threads
        EventPublisher[] publishers = initializePublisherThreads(configuration, executor,
                                                                 transactionManager);
        publisherCount = publishers.length;
        disruptor.handleExceptionsWith(new ExceptionHandler());

        disruptor.handleEventsWith(commandHandlerInvokers).then(publishers);

        coolingDownPeriod = configuration.getCoolingDownPeriod();
        disruptor.start();
    }

    private EventPublisher[] initializePublisherThreads(DisruptorConfiguration configuration, Executor executor,
                                                        TransactionManager transactionManager) {
        EventPublisher[] publishers = new EventPublisher[configuration.getPublisherThreadCount()];
        for (int t = 0; t < publishers.length; t++) {
            publishers[t] = new EventPublisher(executor, transactionManager,
                                               configuration.getRollbackConfiguration(), t);
        }
        return publishers;
    }

    private CommandHandlerInvoker[] initializeInvokerThreads(EventStore eventStore, DisruptorConfiguration configuration) {
        CommandHandlerInvoker[] invokers;
        invokers = new CommandHandlerInvoker[configuration.getInvokerThreadCount()];
        for (int t = 0; t < invokers.length; t++) {
            invokers[t] = new CommandHandlerInvoker(eventStore, configuration.getCache(), t);
        }
        return invokers;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        dispatch(command, FailureLoggingCommandCallback.INSTANCE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        Assert.state(started, "CommandBus has been shut down. It is not accepting any Commands");
        CommandMessage<? extends C> commandToDispatch = command;
        for (MessageDispatchInterceptor<CommandMessage<?>> interceptor : dispatchInterceptors) {
            commandToDispatch = (CommandMessage) interceptor.handle(commandToDispatch);
        }
        doDispatch(commandToDispatch, callback);
    }

    /**
     * Forces a dispatch of a command. This method should be used with caution. It allows commands to be retried during
     * the cooling down period of the disruptor.
     *
     * @param command  The command to dispatch
     * @param callback The callback to notify when command handling is completed
     * @param <R>      The expected return type of the command
     */
    public <C, R> void doDispatch(CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        Assert.state(!disruptorShutDown, "Disruptor has been shut down. Cannot dispatch or re-dispatch commands");
        final MessageHandler<? super CommandMessage<?>> commandHandler = commandHandlers.get(command.getCommandName());
        if (commandHandler == null) {
            throw new NoHandlerForCommandException(
                    format("No handler was subscribed to command [%s]", command.getCommandName()));
        }

        RingBuffer<CommandHandlingEntry> ringBuffer = disruptor.getRingBuffer();
        int invokerSegment = 0;
        int publisherSegment = 0;
        if ((commandHandlerInvokers.length > 1 || publisherCount > 1)) {
            String aggregateIdentifier = commandTargetResolver.resolveTarget(command).getIdentifier();
            if (aggregateIdentifier != null) {
                int idHash = aggregateIdentifier.hashCode() & Integer.MAX_VALUE;
                if (commandHandlerInvokers.length > 1) {
                    invokerSegment = idHash % commandHandlerInvokers.length;
                }
                if (publisherCount > 1) {
                    publisherSegment = idHash % publisherCount;
                }
            }
        }
        long sequence = ringBuffer.next();
        try {
            CommandHandlingEntry event = ringBuffer.get(sequence);
            event.reset(command, commandHandler, invokerSegment, publisherSegment,
                        new BlacklistDetectingCallback<>(callback, disruptor.getRingBuffer(), this,
                                                         rescheduleOnCorruptState), invokerInterceptors,
                        publisherInterceptors);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given
     * <code>aggregateFactory</code>.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     *
     * @param aggregateFactory The factory creating uninitialized instances of the Aggregate
     * @param <T>              The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(AggregateFactory<T> aggregateFactory) {
        return createRepository(aggregateFactory, NoOpEventStreamDecorator.INSTANCE);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given
     * <code>aggregateFactory</code>. The given <code>decorator</code> is used to decorate event streams.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     * <p>
     * Note that a second invocation of this method with an aggregate factory for the same aggregate type <em>may</em>
     * return the same instance as the first invocation, even if the given <code>decorator</code> is different.
     *
     * @param aggregateFactory The factory creating uninitialized instances of the Aggregate
     * @param decorator        The decorator to decorate events streams with
     * @param <T>              The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(AggregateFactory<T> aggregateFactory, EventStreamDecorator decorator) {
        for (CommandHandlerInvoker invoker : commandHandlerInvokers) {
            invoker.createRepository(aggregateFactory, decorator);
        }
        return new DisruptorRepository<>(aggregateFactory.getAggregateType());
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        commandHandlers.put(commandName, handler);
        return () -> commandHandlers.remove(commandName, handler);
    }

    /**
     * Shuts down the command bus. It no longer accepts new commands, and finishes processing commands that have
     * already been published. This method will not shut down any executor that has been provided as part of the
     * Configuration.
     */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        long lastChangeDetected = System.currentTimeMillis();
        long lastKnownCursor = disruptor.getRingBuffer().getCursor();
        while (System.currentTimeMillis() - lastChangeDetected < coolingDownPeriod && !Thread.interrupted()) {
            if (disruptor.getRingBuffer().getCursor() != lastKnownCursor) {
                lastChangeDetected = System.currentTimeMillis();
                lastKnownCursor = disruptor.getRingBuffer().getCursor();
            }
        }
        disruptorShutDown = true;
        disruptor.shutdown();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private static class FailureLoggingCommandCallback implements CommandCallback<Object, Object> {

        private static final FailureLoggingCommandCallback INSTANCE = new FailureLoggingCommandCallback();

        private FailureLoggingCommandCallback() {
        }

        @Override
        public void onSuccess(CommandMessage<?> commandMessage, Object result) {
        }

        @Override
        public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
            logger.info("An error occurred while handling a command [{}].", commandMessage.getCommandName(), cause);
        }
    }

    private static class DisruptorRepository<T> implements Repository<T> {

        private final Class<T> type;

        public DisruptorRepository(Class<T> type) {
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Aggregate<T> load(String aggregateIdentifier, Long expectedVersion) {
            return (Aggregate<T>) CommandHandlerInvoker.getRepository(type).load(aggregateIdentifier, expectedVersion);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Aggregate<T> load(String aggregateIdentifier) {
            return (Aggregate<T>) CommandHandlerInvoker.getRepository(type).load(aggregateIdentifier);
        }

        @Override
        public Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception {
            return CommandHandlerInvoker.<T>getRepository(type).newInstance(factoryMethod);
        }
    }

    private static class NoOpEventStreamDecorator implements EventStreamDecorator {

        public static final EventStreamDecorator INSTANCE = new NoOpEventStreamDecorator();

        @Override
        public DomainEventStream decorateForRead(String aggregateIdentifier,
                                                 DomainEventStream eventStream) {
            return eventStream;
        }

        @Override
        public List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate,
                                                             List<DomainEventMessage<?>> events) {
            return events;
        }
    }

    private class ExceptionHandler implements com.lmax.disruptor.ExceptionHandler {

        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            logger.error("Exception occurred while processing a {}.",
                         ((CommandHandlingEntry) event).getMessage().getPayloadType().getSimpleName(), ex);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            logger.error("Failed to start the DisruptorCommandBus.", ex);
            disruptor.shutdown();
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            logger.error("Error while shutting down the DisruptorCommandBus", ex);
        }
    }
}
