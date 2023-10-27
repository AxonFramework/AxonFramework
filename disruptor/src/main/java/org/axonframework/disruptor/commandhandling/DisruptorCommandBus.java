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

package org.axonframework.disruptor.commandhandling;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolution;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.MonitorAwareCallback;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.Registration;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.NoCache;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.axonframework.common.BuilderUtils.*;
import static org.axonframework.common.ObjectUtils.getOrDefault;

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
 *
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
 * default. Use {@link Builder#rescheduleCommandsOnCorruptState(boolean)} to disable this feature. Note
 * that the order in which commands are executed is not fully guaranteed when this feature is enabled (default).
 *
 * <em>Limitations of this implementation</em>
 * <p>
 * Although this implementation allows applications to achieve extreme performance (over 1M commands on commodity
 * hardware), it does have some limitations. It only allows a single aggregate to be invoked during command processing.
 * <p>
 * This implementation can only work with Event Sourced Aggregates.
 *
 * <em>Infrastructure considerations</em>
 * <p>
 * This CommandBus implementation has special requirements for the Repositories being used during Command Processing.
 * Therefore, the Repository instance to use in the Command Handler must be created using {@link
 * #createRepository(EventStore, AggregateFactory, RepositoryProvider)}.
 * Using another repository will most likely result in undefined behavior.
 * <p>
 * The DisruptorCommandBus must have access to at least 3 threads, two of which are permanently used while the
 * DisruptorCommandBus is operational. At least one additional thread is required to invoke callbacks and initiate a
 * recovery process in the case of exceptions.
 * <p>
 * Consider providing an alternative {@link IdentifierFactory} implementation. The default
 * implementation used {@link java.util.UUID#randomUUID()} to generated identifier for Events. The poor performance of
 * this method severely impacts overall performance of the DisruptorCommandBus. A better performing alternative is, for
 * example, <a href="http://johannburkard.de/software/uuid/" target="_blank">{@code com.eaio.uuid.UUID}</a>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(DisruptorCommandBus.class);

    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> commandHandlers =
            new ConcurrentHashMap<>();

    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> invokerInterceptors;
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> CompletableFutureInterceptors;
    private final ExecutorService executorService;
    private final boolean rescheduleOnCorruptState;
    private final long coolingDownPeriod;
    private final CommandTargetResolver commandTargetResolver;
    private final int CompletableFutureCount;
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;
    private final Disruptor<CommandHandlingEntry> disruptor;
    private final CommandHandlerInvoker[] commandHandlerInvokers;
    private final DuplicateCommandHandlerResolver duplicateCommandHandlerResolver;
    private final CommandCallback<Object, Object> defaultCommandCallback;

    private volatile boolean started = true;
    private volatile boolean disruptorShutDown = false;

    /**
     * Instantiate a Builder to be able to create a {@link DisruptorCommandBus}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The {@code rescheduleCommandsOnCorruptState} defaults to {@code true}.</li>
     * <li>The {@code coolingDownPeriod} defaults to {@code 1000}.</li>
     * <li>The {@link CommandTargetResolver} defaults to an {@link AnnotationCommandTargetResolver}.</li>
     * <li>The {@code CompletableFutureThreadCount} defaults to {@code 1}.</li>
     * <li>The {@link MessageMonitor} defaults to {@link NoOpMessageMonitor#INSTANCE}.</li>
     * <li>The {@link RollbackConfiguration} defaults to {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}.</li>
     * <li>The {@code bufferSize} defaults to {@code 4096}.</li>
     * <li>The {@link ProducerType} defaults to {@link ProducerType#MULTI}.</li>
     * <li>The {@link WaitStrategy} defaults to a {@link BlockingWaitStrategy}.</li>
     * <li>The {@code invokerThreadCount} defaults to {@code 1}.</li>
     * <li>The {@link Cache} defaults to {@link NoCache#INSTANCE}.</li>
     * <li>The {@link DuplicateCommandHandlerResolver} defaults to {@link DuplicateCommandHandlerResolution#logAndOverride()}.</li>
     * </ul>
     * The (2) Threads required for command execution are created immediately. Additional threads are used to invoke
     * response callbacks and to initialize a recovery process in the case of errors. The thread creation process can
     * be specified by providing an {@link Executor}.
     * <p>
     * The {@link CommandTargetResolver}, {@link MessageMonitor}, {@link RollbackConfiguration}, {@link ProducerType},
     * {@link WaitStrategy} and {@link Cache} are a <b>hard requirements</b>. Thus setting them to {@code null} will
     * result in an {@link AxonConfigurationException}.
     * Additionally, the {@code coolingDownPeriod}, {@code CompletableFutureThreadCount}, {@code bufferSize} and
     * {@code invokerThreadCount} have a positive number constraint, thus will also result in an
     * AxonConfigurationException if set otherwise.
     *
     * @return a Builder to be able to create a {@link DisruptorCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link DisruptorCommandBus} based on the fields contained in the {@link Builder}. The Threads
     * required for command execution are immediately requested from the Configuration's Executor, if any. Otherwise,
     * they are created.
     * <p>
     * Will assert that the {@link CommandTargetResolver}, {@link MessageMonitor}, {@link RollbackConfiguration}, {@link
     * ProducerType}, {@link WaitStrategy} and {@link Cache} are not {@code null}. Additional verification is done on
     * the the {@code coolingDownPeriod}, {@code CompletableFutureThreadCount}, {@code bufferSize} and {@code
     * invokerThreadCount} to check whether they are positive numbers. If any of these checks fails, an {@link
     * AxonConfigurationException} will be thrown.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DisruptorCommandBus} instance
     */
    protected DisruptorCommandBus(Builder builder) {
        builder.validate();

        dispatchInterceptors = new CopyOnWriteArrayList<>(builder.dispatchInterceptors);
        invokerInterceptors = new CopyOnWriteArrayList<>(builder.invokerInterceptors);
        CompletableFutureInterceptors = new ArrayList<>(builder.CompletableFutureInterceptors);

        Executor executor = builder.executor;
        if (executor == null) {
            executorService = Executors.newCachedThreadPool(new AxonThreadFactory("DisruptorCommandBus"));
            executor = executorService;
        } else {
            executorService = null;
        }
        rescheduleOnCorruptState = builder.rescheduleCommandsOnCorruptState;
        coolingDownPeriod = builder.coolingDownPeriod;
        commandTargetResolver = builder.commandTargetResolver;
        defaultCommandCallback = builder.defaultCommandCallback;

        // Configure CompletableFuture Threads
        EventCompletableFuture[] CompletableFutures = initializeCompletableFutureThreads(builder.CompletableFutureThreadCount,
                                                                 executor,
                                                                 builder.transactionManager,
                                                                 builder.rollbackConfiguration);
        CompletableFutureCount = CompletableFutures.length;
        messageMonitor = builder.messageMonitor;
        duplicateCommandHandlerResolver = builder.duplicateCommandHandlerResolver;

        disruptor = new Disruptor<>(CommandHandlingEntry::new,
                                    builder.bufferSize,
                                    executor,
                                    builder.producerType,
                                    builder.waitStrategy);
        // Configure invoker Threads
        commandHandlerInvokers = initializeInvokerThreads(builder.invokerThreadCount, builder.cache);

        disruptor.setDefaultExceptionHandler(new ExceptionHandler());
        disruptor.handleEventsWith(commandHandlerInvokers).then(CompletableFutures);
        disruptor.start();
    }

    private EventCompletableFuture[] initializeCompletableFutureThreads(int CompletableFutureThreadCount,
                                                        Executor executor,
                                                        TransactionManager transactionManager,
                                                        RollbackConfiguration rollbackConfiguration) {
        EventCompletableFuture[] CompletableFutures = new EventCompletableFuture[CompletableFutureThreadCount];
        Arrays.setAll(CompletableFutures, t -> new EventCompletableFuture(executor, transactionManager, rollbackConfiguration, t));
        return CompletableFutures;
    }

    private CommandHandlerInvoker[] initializeInvokerThreads(int invokerThreadCount, Cache cache) {
        CommandHandlerInvoker[] invokers = new CommandHandlerInvoker[invokerThreadCount];
        Arrays.setAll(invokers, t -> new CommandHandlerInvoker(cache, t));
        return invokers;
    }

    @Override
    public <C> void dispatch(@Nonnull CommandMessage<C> command) {
        dispatch(command, defaultCommandCallback);
    }

    @Override
    public <C, R> void dispatch(@Nonnull CommandMessage<C> command,
                                @Nonnull CommandCallback<? super C, ? super R> callback) {
        Assert.state(started, () -> "CommandBus has been shut down. It is not accepting any Commands");
        CommandMessage<? extends C> commandToDispatch = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            commandToDispatch = (CommandMessage<? extends C>) interceptor.handle(commandToDispatch);
        }
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(commandToDispatch);

        try {
            doDispatch(commandToDispatch, new MonitorAwareCallback<>(callback, monitorCallback));
        } catch (Exception e) {
            monitorCallback.reportFailure(e);
            callback.onResult(commandToDispatch, asCommandResultMessage(e));
        }
    }

    /**
     * Forces a dispatch of a command. This method should be used with caution. It allows commands to be retried during
     * the cooling down period of the disruptor.
     *
     * @param command  The command to dispatch
     * @param callback The callback to notify when command handling is completed
     * @param <R>      The expected return type of the command
     */
    @SuppressWarnings("Duplicates")
    private <C, R> void doDispatch(CommandMessage<? extends C> command, CommandCallback<? super C, R> callback) {
        Assert.state(!disruptorShutDown, () -> "Disruptor has been shut down. Cannot dispatch or re-dispatch commands");
        final MessageHandler<? super CommandMessage<?>> commandHandler = commandHandlers.get(command.getCommandName());
        if (commandHandler == null) {
            callback.onResult(command, asCommandResultMessage(new NoHandlerForCommandException(format(
                    "No handler was subscribed for command [%s].", command.getCommandName()
            ))));
            return;
        }

        RingBuffer<CommandHandlingEntry> ringBuffer = disruptor.getRingBuffer();
        int invokerSegment = 0;
        int CompletableFutureSegment = 0;
        if (commandHandlerInvokers.length > 1 || CompletableFutureCount > 1) {
            String aggregateIdentifier = commandTargetResolver.resolveTarget(command).getIdentifier();
            if (aggregateIdentifier != null) {
                int idHash = aggregateIdentifier.hashCode() & Integer.MAX_VALUE;
                if (commandHandlerInvokers.length > 1) {
                    invokerSegment = idHash % commandHandlerInvokers.length;
                }
                if (CompletableFutureCount > 1) {
                    CompletableFutureSegment = idHash % CompletableFutureCount;
                }
            }
        }
        long sequence = ringBuffer.next();
        try {
            CommandHandlingEntry event = ringBuffer.get(sequence);
            event.reset(command, commandHandler, invokerSegment, CompletableFutureSegment,
                        new BlacklistDetectingCallback<C, R>(
                                callback, disruptor.getRingBuffer(), this::doDispatch, rescheduleOnCorruptState
                        ),
                        invokerInterceptors,
                        CompletableFutureInterceptors);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given {@code eventStore} and
     * {@code aggregateFactory}.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     *
     * @param eventStore       The Event Store to retrieve and persist events
     * @param aggregateFactory The factory creating uninitialized instances of the Aggregate
     * @param <T>              The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore, AggregateFactory<T> aggregateFactory) {
        return createRepository(eventStore, aggregateFactory, NoSnapshotTriggerDefinition.INSTANCE);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given {@code eventStore} and
     * {@code aggregateFactory}.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     *
     * @param eventStore         The Event Store to retrieve and persist events
     * @param aggregateFactory   The factory creating uninitialized instances of the Aggregate
     * @param repositoryProvider Provides repositories for specified aggregate types
     * @param <T>                The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              RepositoryProvider repositoryProvider) {
        return createRepository(eventStore, aggregateFactory, NoSnapshotTriggerDefinition.INSTANCE, repositoryProvider);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate, source from given {@code eventStore}, that is
     * created by the given {@code aggregateFactory}.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     *
     * @param eventStore                The Event Store to retrieve and persist events
     * @param aggregateFactory          The factory creating uninitialized instances of the Aggregate
     * @param snapshotTriggerDefinition The trigger definition for creating snapshots
     * @param <T>                       The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition) {
        return createRepository(eventStore,
                                aggregateFactory,
                                snapshotTriggerDefinition,
                                ClasspathParameterResolverFactory.forClass(aggregateFactory.getAggregateType()));
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate, source from given {@code eventStore}, that is
     * created by the given {@code aggregateFactory}. The given {@code decorator} is used to decorate event streams.
     * <p>
     * The repository returned must be used by Command Handlers subscribed to this Command Bus for loading aggregate
     * instances. Using any other repository instance may result in undefined outcome (a.k.a. concurrency problems).
     * <p>
     * Note that a second invocation of this method with an aggregate factory for the same aggregate type <em>may</em>
     * return the same instance as the first invocation, even if the given {@code decorator} is different.
     *
     * @param eventStore                The Event Store to retrieve and persist events
     * @param aggregateFactory          The factory creating uninitialized instances of the Aggregate
     * @param snapshotTriggerDefinition The trigger definition for creating snapshots
     * @param repositoryProvider        Provides repositories for specified aggregate types
     * @param <T>                       The type of aggregate to create the repository for
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              RepositoryProvider repositoryProvider) {
        return createRepository(eventStore,
                                aggregateFactory,
                                snapshotTriggerDefinition,
                                ClasspathParameterResolverFactory.forClass(aggregateFactory.getAggregateType()),
                                ClasspathHandlerDefinition.forClass(aggregateFactory.getAggregateType()),
                                repositoryProvider);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given {@code
     * aggregateFactory} and sourced from given {@code eventStore}. Parameters of the annotated methods are resolved
     * using the given {@code parameterResolverFactory}.
     *
     * @param eventStore               The Event Store to retrieve and persist events
     * @param aggregateFactory         The factory creating uninitialized instances of the Aggregate
     * @param parameterResolverFactory The ParameterResolverFactory to resolve parameter values of annotated handler
     *                                 with
     * @param <T>                      The type of aggregate managed by this repository
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              ParameterResolverFactory parameterResolverFactory) {
        return createRepository(eventStore,
                                aggregateFactory,
                                NoSnapshotTriggerDefinition.INSTANCE,
                                parameterResolverFactory);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate that is created by the given {@code
     * aggregateFactory} and sourced from given {@code eventStore}. Parameters of the annotated methods are resolved
     * using the given {@code parameterResolverFactory}. The given {@code handlerDefinition} is used to create handler
     * instances.
     *
     * @param eventStore               The Event Store to retrieve and persist events
     * @param aggregateFactory         The factory creating uninitialized instances of the Aggregate
     * @param parameterResolverFactory The ParameterResolverFactory to resolve parameter values of annotated handler
     *                                 with
     * @param handlerDefinition        The handler definition used to create concrete handlers
     * @param repositoryProvider       Provides specific for given aggregate types
     * @param <T>                      The type of aggregate managed by this repository
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              ParameterResolverFactory parameterResolverFactory,
                                              HandlerDefinition handlerDefinition,
                                              RepositoryProvider repositoryProvider) {
        return createRepository(eventStore,
                                aggregateFactory,
                                NoSnapshotTriggerDefinition.INSTANCE,
                                parameterResolverFactory,
                                handlerDefinition,
                                repositoryProvider);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate, sourced from given {@code eventStore}, that is
     * created by the given {@code aggregateFactory}. Parameters of the annotated methods are resolved using the given
     * {@code parameterResolverFactory}.
     *
     * @param eventStore                The Event Store to retrieve and persist events
     * @param aggregateFactory          The factory creating uninitialized instances of the Aggregate
     * @param snapshotTriggerDefinition The trigger definition for snapshots
     * @param parameterResolverFactory  The ParameterResolverFactory to resolve parameter values of annotated handler
     *                                  with
     * @param <T>                       The type of aggregate managed by this repository
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              ParameterResolverFactory parameterResolverFactory) {
        return createRepository(eventStore,
                                aggregateFactory,
                                snapshotTriggerDefinition,
                                parameterResolverFactory,
                                ClasspathHandlerDefinition.forClass(aggregateFactory.getAggregateType()),
                                null);
    }

    /**
     * Creates a repository instance for an Event Sourced aggregate, sourced from given {@code eventStore}, that is
     * created by the given {@code aggregateFactory}. Parameters of the annotated methods are resolved using the given
     * {@code parameterResolverFactory}. The given {@code handlerDefinition} is used to create handler instances.
     *
     * @param eventStore                The Event Store to retrieve and persist events
     * @param aggregateFactory          The factory creating uninitialized instances of the Aggregate
     * @param snapshotTriggerDefinition The trigger definition for snapshots
     * @param parameterResolverFactory  The ParameterResolverFactory to resolve parameter values of annotated handler
     *                                  with
     * @param handlerDefinition         The handler definition used to create concrete handlers
     * @param repositoryProvider        Provides repositories for specific aggregate types
     * @param <T>                       The type of aggregate managed by this repository
     * @return the repository that provides access to stored aggregates
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              ParameterResolverFactory parameterResolverFactory,
                                              HandlerDefinition handlerDefinition,
                                              RepositoryProvider repositoryProvider) {
        for (CommandHandlerInvoker invoker : commandHandlerInvokers) {
            invoker.createRepository(eventStore,
                                     repositoryProvider,
                                     aggregateFactory,
                                     snapshotTriggerDefinition,
                                     parameterResolverFactory,
                                     handlerDefinition);
        }
        return new DisruptorRepository<>(aggregateFactory.getAggregateType());
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>> handler) {
        logger.debug("Subscribing command with name [{}]", commandName);
        commandHandlers.compute(commandName, (cn, existingHandler) -> {
            if (existingHandler == null || existingHandler == handler) {
                return handler;
            } else {
                return duplicateCommandHandlerResolver.resolve(cn, existingHandler, handler);
            }
        });
        return () -> commandHandlers.remove(commandName, handler);
    }

    /**
     * Shuts down the command bus. It no longer accepts new commands, and finishes processing commands that have already
     * been published. This method <b>will not</b> shut down any executor that has been provided as part of the Builder
     * process.
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

    @Override
    public @Nonnull
    Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        invokerInterceptors.add(handlerInterceptor);
        return () -> invokerInterceptors.remove(handlerInterceptor);
    }

    private static class FailureLoggingCommandCallback implements CommandCallback<Object, Object> {

        private static final FailureLoggingCommandCallback INSTANCE = new FailureLoggingCommandCallback();

        private FailureLoggingCommandCallback() {
        }

        @Override
        public void onResult(@Nonnull CommandMessage<?> commandMessage,
                             @Nonnull CommandResultMessage<?> commandResultMessage) {
            if (commandResultMessage.isExceptional()) {
                logger.info("An error occurred while handling a command [{}].",
                            commandMessage.getCommandName(),
                            commandResultMessage.exceptionResult());
            }
        }
    }

    /**
     * Builder class to instantiate a {@link DisruptorCommandBus}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The {@code rescheduleCommandsOnCorruptState} defaults to {@code true}.</li>
     * <li>The {@code coolingDownPeriod} defaults to {@code 1000}.</li>
     * <li>The {@link CommandTargetResolver} defaults to an {@link AnnotationCommandTargetResolver}.</li>
     * <li>The {@code CompletableFutureThreadCount} defaults to {@code 1}.</li>
     * <li>The {@link MessageMonitor} defaults to {@link NoOpMessageMonitor#INSTANCE}.</li>
     * <li>The {@link RollbackConfiguration} defaults to {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}.</li>
     * <li>The {@code bufferSize} defaults to {@code 4096}.</li>
     * <li>The {@link ProducerType} defaults to {@link ProducerType#MULTI}.</li>
     * <li>The {@link WaitStrategy} defaults to a {@link BlockingWaitStrategy}.</li>
     * <li>The {@code invokerThreadCount} defaults to {@code 1}.</li>
     * <li>The {@link Cache} defaults to {@link NoCache#INSTANCE}.</li>
     * <li>The {@link DuplicateCommandHandlerResolver} defaults to {@link DuplicateCommandHandlerResolution#logAndOverride()}.</li>
     * </ul>
     * The (2) Threads required for command execution are created immediately. Additional threads are used to invoke
     * response callbacks and to initialize a recovery process in the case of errors. The thread creation process can
     * be specified by providing an {@link Executor}.
     * <p>
     * The {@link CommandTargetResolver}, {@link MessageMonitor}, {@link RollbackConfiguration}, {@link ProducerType},
     * {@link WaitStrategy} and {@link Cache} are a <b>hard requirements</b>. Thus setting them to {@code null} will
     * result in an {@link AxonConfigurationException}.
     * Additionally, the {@code coolingDownPeriod}, {@code CompletableFutureThreadCount}, {@code bufferSize} and
     * {@code invokerThreadCount} have a positive number constraint, thus will also result in an
     * AxonConfigurationException if set otherwise.
     */
    public static class Builder {

        private static final int DEFAULT_BUFFER_SIZE = 4096;

        private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> invokerInterceptors =
                new ArrayList<>();
        private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> CompletableFutureInterceptors =
                new ArrayList<>();
        private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors =
                new ArrayList<>();
        private Executor executor;
        private boolean rescheduleCommandsOnCorruptState = true;
        private long coolingDownPeriod = 1000;
        private CommandTargetResolver commandTargetResolver = AnnotationCommandTargetResolver.builder().build();
        private int CompletableFutureThreadCount = 1;
        private MessageMonitor<? super CommandMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private TransactionManager transactionManager;
        private RollbackConfiguration rollbackConfiguration = RollbackConfigurationType.UNCHECKED_EXCEPTIONS;
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private ProducerType producerType = ProducerType.MULTI;
        private WaitStrategy waitStrategy = new BlockingWaitStrategy();
        private int invokerThreadCount = 1;
        private Cache cache = NoCache.INSTANCE;
        private DuplicateCommandHandlerResolver duplicateCommandHandlerResolver = DuplicateCommandHandlerResolution.logAndOverride();
        private CommandCallback<Object, Object> defaultCommandCallback = FailureLoggingCommandCallback.INSTANCE;

        /**
         * Set the {@link MessageHandlerInterceptor} of generic type {@link CommandMessage} to use with the {@link
         * DisruptorCommandBus} during in the invocation thread. The interceptors are invoked by the thread that also
         * executes the command handler.
         * <p/>
         * Note that this is *not* the thread that stores and publishes the generated events. See {@link
         * #CompletableFutureInterceptors(java.util.List)}.
         *
         * @param invokerInterceptors the {@link MessageHandlerInterceptor}s to invoke when handling an incoming
         *                            command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder invokerInterceptors(
                List<MessageHandlerInterceptor<? super CommandMessage<?>>> invokerInterceptors
        ) {
            this.invokerInterceptors.clear();
            this.invokerInterceptors.addAll(invokerInterceptors);
            return this;
        }

        /**
         * Configures the {@link MessageHandlerInterceptor} of generic type {@link CommandMessage}  to use with the
         * {@link DisruptorCommandBus} during the publication of changes. The interceptors are invoked by the thread
         * that also stores and publishes the events.
         *
         * @param CompletableFutureInterceptors the {@link MessageHandlerInterceptor}s to invoke when handling an incoming
         *                              command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder CompletableFutureInterceptors(List<MessageHandlerInterceptor<CommandMessage<?>>> CompletableFutureInterceptors) {
            this.CompletableFutureInterceptors.clear();
            this.CompletableFutureInterceptors.addAll(CompletableFutureInterceptors);
            return this;
        }

        /**
         * Configures {@link MessageDispatchInterceptor} of generic type {@link CommandMessage} to use with the {@link
         * DisruptorCommandBus} when commands are dispatched. The interceptors are invoked by the thread that provides
         * the commands to the command bus.
         *
         * @param dispatchInterceptors the {@link MessageDispatchInterceptor}s dispatch interceptors to invoke when
         *                             dispatching a command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(List<MessageDispatchInterceptor<CommandMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors.clear();
            this.dispatchInterceptors.addAll(dispatchInterceptors);
            return this;
        }

        /**
         * Sets the {@link Executor} that provides the processing resources (Threads) for the components of the {@link
         * DisruptorCommandBus}. The provided executor must be capable of providing the required number of threads.
         * Three threads are required immediately at startup and will not be returned until the CommandBus is stopped.
         * Additional threads are used to invoke callbacks and start a recovery process in case aggregate state has been
         * corrupted. Failure to do this results in the disruptor hanging at startup, waiting for resources to become
         * available.
         * <p/>
         * Defaults to {@code null}, causing the DisruptorCommandBus to create the necessary threads itself. In that
         * case, threads are created in the DisruptorCommandBus ThreadGroup.
         *
         * @param executor the {@link Executor} that provides the processing resources
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Set the indicator specifying whether commands that failed because they were executed against potentially
         * corrupted aggregate state should be automatically rescheduled. Commands that caused the aggregate state to
         * become corrupted are <em>never</em> automatically rescheduled, to prevent poison message syndrome.
         * <p/>
         * Defaults to {@code true}.
         *
         * @param rescheduleCommandsOnCorruptState a {@code boolean} specifying whether or not to automatically
         *                                         reschedule commands that failed due to potentially corrupted
         *                                         aggregate state.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder rescheduleCommandsOnCorruptState(boolean rescheduleCommandsOnCorruptState) {
            this.rescheduleCommandsOnCorruptState = rescheduleCommandsOnCorruptState;
            return this;
        }

        /**
         * Sets the cooling down period in milliseconds. This is the time in which new commands are no longer accepted,
         * but the {@link DisruptorCommandBus} may reschedule commands that may have been executed against a corrupted
         * Aggregate. If no commands have been rescheduled during this period, the disruptor shuts down completely.
         * Otherwise, it wait until no commands were scheduled for processing.
         * <p/>
         * Defaults to 1000 ms (1 second).
         *
         * @param coolingDownPeriod a {@code long} specifying the cooling down period for the shutdown of the {@link
         *                          DisruptorCommandBus}, in milliseconds.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder coolingDownPeriod(long coolingDownPeriod) {
            assertStrictPositive(coolingDownPeriod, "The cooling down period must be a positive number");
            this.coolingDownPeriod = coolingDownPeriod;
            return this;
        }

        /**
         * Sets the {@link CommandTargetResolver} that must be used to indicate which Aggregate instance will be invoked
         * by an incoming command. The {@link DisruptorCommandBus} only uses this value if {@link
         * #invokerThreadCount(int)}}, or {@link #CompletableFutureThreadCount(int)} is greater than {@code 1}.
         * <p/>
         * Defaults to an {@link AnnotationCommandTargetResolver} instance.
         *
         * @param commandTargetResolver The {@link CommandTargetResolver} to use to indicate which Aggregate instance is
         *                              target of an incoming Command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder commandTargetResolver(CommandTargetResolver commandTargetResolver) {
            assertNonNull(commandTargetResolver, "CommandTargetResolver may not be null");
            this.commandTargetResolver = commandTargetResolver;
            return this;
        }

        /**
         * Sets the number of Threads that should be used to store and publish the generated Events. Defaults to {@code
         * 1}.
         * <p/>
         * A good value for this setting mainly depends on the number of cores your machine has, as well as the amount
         * of I/O that the process requires. If no I/O is involved, a good starting value is {@code [processors / 2]}.
         *
         * @param CompletableFutureThreadCount the number of Threads to use for publishing as an {@code int}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder CompletableFutureThreadCount(int CompletableFutureThreadCount) {
            assertStrictPositive(CompletableFutureThreadCount, "The CompletableFuture thread count must at least be 1");
            this.CompletableFutureThreadCount = CompletableFutureThreadCount;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} of generic type {@link CommandMessage} used the to monitor the command bus.
         * Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} used the message monitor to monitor the command bus
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(MessageMonitor<? super CommandMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} to use to manage a transaction around the storage and publication of
         * events. The default ({@code null}) is to not have publication and storage of events wrapped in a
         * transaction.
         *
         * @param transactionManager the {@link TransactionManager} to use to manage a transaction around the storage
         *                           and publication of events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link RollbackConfiguration} which allows you to specify when a {@link UnitOfWork} should be rolled
         * back. Defaults to a {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, which triggers a rollback on all
         * unchecked exceptions.
         *
         * @param rollbackConfiguration a {@link RollbackConfiguration} specifying when a {@link UnitOfWork} should be
         *                              rolled back
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder rollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
            assertNonNull(rollbackConfiguration, "RollbackConfiguration may not be null");
            this.rollbackConfiguration = rollbackConfiguration;
            return this;
        }

        /**
         * Sets the buffer size to use. This field must be positive and a power of 2.
         * <p>
         * The default is {@code 4096}.
         *
         * @param bufferSize an {@code int} specifying the buffer size to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder bufferSize(int bufferSize) {
            assertThat(bufferSize, size -> size > 0 && size % 2 == 0,
                       "The buffer size must be positive and a power of 2");
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Sets the {@link ProducerType} to use by the {@link Disruptor}.
         * <p>
         * Defaults to a {@link ProducerType#MULTI} solution.
         *
         * @param producerType the {@link ProducerType} to use by the {@link Disruptor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder producerType(ProducerType producerType) {
            assertNonNull(producerType, "ProducerType may not be null");
            this.producerType = producerType;
            return this;
        }

        /**
         * Sets the {@link WaitStrategy} which is used to make dependent threads wait for tasks to be completed. The
         * choice of strategy mainly depends on the number of processors available and the number of tasks other than
         * the {@link DisruptorCommandBus} being processed.
         * <p/>
         * The {@link com.lmax.disruptor.BusySpinWaitStrategy} provides the best throughput at the lowest latency, but
         * also put a big claim on available CPU resources. The {@link com.lmax.disruptor.SleepingWaitStrategy} yields
         * lower performance, but leaves resources available for other processes to use.
         * <p/>
         * Defaults to the {@link BlockingWaitStrategy}.
         *
         * @param waitStrategy The WaitStrategy to use
         * @return the current Builder instance, for fluent interfacing
         * @see com.lmax.disruptor.SleepingWaitStrategy SleepingWaitStrategy
         * @see com.lmax.disruptor.BlockingWaitStrategy BlockingWaitStrategy
         * @see com.lmax.disruptor.BusySpinWaitStrategy BusySpinWaitStrategy
         * @see com.lmax.disruptor.YieldingWaitStrategy YieldingWaitStrategy
         */
        public Builder waitStrategy(WaitStrategy waitStrategy) {
            assertNonNull(waitStrategy, "WaitStrategy may not be null");
            this.waitStrategy = waitStrategy;
            return this;
        }

        /**
         * Sets the number of Threads that should be used to invoke the Command Handlers. Defaults to {@code 1}.
         * <p/>
         * A good value for this setting mainly depends on the number of cores your machine has, as well as the amount
         * of I/O that the process requires. A good range, if no I/O is involved is {@code 1 .. ([processor count] /
         * 2)}.
         *
         * @param invokerThreadCount an {@code int} specifying the number of Threads to use for Command Handler
         *                           invocation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder invokerThreadCount(int invokerThreadCount) {
            assertStrictPositive(invokerThreadCount, "The invoker thread count must be at least 1");
            this.invokerThreadCount = invokerThreadCount;
            return this;
        }

        /**
         * Sets the {@link Cache} in which loaded aggregates will be stored. Aggregates that are not active in the
         * CommandBus' buffer will be loaded from this cache. If they are not in the cache, a new instance will be
         * constructed using Events from the {@link EventStore}.
         * <p/>
         * By default, no cache is used.
         *
         * @param cache the cache to store loaded aggregates in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder cache(Cache cache) {
            assertNonNull(cache, "Cache may not be null");
            this.cache = cache;
            return this;
        }

        /**
         * Sets the {@link DuplicateCommandHandlerResolver} used to resolves the road to take when a duplicate command
         * handler is subscribed. Defaults to {@link DuplicateCommandHandlerResolution#logAndOverride() Log and
         * Override}.
         *
         * @param duplicateCommandHandlerResolver a {@link DuplicateCommandHandlerResolver} used to resolves the road to
         *                                        take when a duplicate command handler is subscribed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder duplicateCommandHandlerResolver(
                DuplicateCommandHandlerResolver duplicateCommandHandlerResolver
        ) {
            assertNonNull(duplicateCommandHandlerResolver, "DuplicateCommandHandlerResolver may not be null");
            this.duplicateCommandHandlerResolver = duplicateCommandHandlerResolver;
            return this;
        }

        /**
         * Sets the callback to use when commands are dispatched in a "fire and forget" method, such as {@link
         * #dispatch(CommandMessage)}. Defaults to a {@link FailureLoggingCommandCallback}, which logs failed commands
         * to a logger. Passing {@code null} will result in a {@link NoOpCallback} being used.
         *
         * @param defaultCommandCallback the callback to invoke when no explicit callback is provided for a command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultCommandCallback(CommandCallback<Object, Object> defaultCommandCallback) {
            this.defaultCommandCallback = getOrDefault(defaultCommandCallback, NoOpCallback.INSTANCE);
            return this;
        }


        /**
         * Initializes a {@link DisruptorCommandBus} as specified through this Builder.
         *
         * @return a {@link DisruptorCommandBus} as specified through this Builder
         */
        public DisruptorCommandBus build() {
            return new DisruptorCommandBus(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            // Method kept for overriding
        }
    }

    private class DisruptorRepository<T> implements Repository<T> {

        private final Class<T> type;

        public DisruptorRepository(Class<T> type) {
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Aggregate<T> load(@Nonnull String aggregateIdentifier, Long expectedVersion) {
            return (Aggregate<T>) CommandHandlerInvoker.getRepository(type).load(aggregateIdentifier, expectedVersion);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Aggregate<T> load(@Nonnull String aggregateIdentifier) {
            return (Aggregate<T>) CommandHandlerInvoker.getRepository(type).load(aggregateIdentifier);
        }

        @Override
        public Aggregate<T> newInstance(@Nonnull Callable<T> factoryMethod) throws Exception {
            return CommandHandlerInvoker.<T>getRepository(type).newInstance(factoryMethod);
        }

        @Override
        public Aggregate<T> loadOrCreate(@Nonnull String aggregateIdentifier, @Nonnull Callable<T> factoryMethod)
                throws Exception {
            return CommandHandlerInvoker.<T>getRepository(type).loadOrCreate(aggregateIdentifier, factoryMethod);
        }

        @Override
        public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
            CompletableFuture<?> future = new CompletableFuture<>();
            send(message, scopeDescription, future);
            try {
                future.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                }
                throw e;
            }
        }

        @SuppressWarnings("Duplicates")
        private void send(Message<?> message, ScopeDescriptor scopeDescription, CompletableFuture<?> future) {
            if (!canResolve(scopeDescription)) {
                future.complete(null);
                return;
            }

            String aggregateIdentifier = ((AggregateScopeDescriptor) scopeDescription).getIdentifier().toString();

            RingBuffer<CommandHandlingEntry> ringBuffer = disruptor.getRingBuffer();
            int invokerSegment = 0;
            int CompletableFutureSegment = 0;
            if (commandHandlerInvokers.length > 1 || CompletableFutureCount > 1) {
                if (aggregateIdentifier != null) {
                    int idHash = aggregateIdentifier.hashCode() & Integer.MAX_VALUE;
                    if (commandHandlerInvokers.length > 1) {
                        invokerSegment = idHash % commandHandlerInvokers.length;
                    }
                    if (CompletableFutureCount > 1) {
                        CompletableFutureSegment = idHash % CompletableFutureCount;
                    }
                }
            }

            long sequence = ringBuffer.next();
            try {
                CommandHandlingEntry event = ringBuffer.get(sequence);
                event.resetAsCallable(
                        () -> {
                            try {
                                return load(aggregateIdentifier).handle(message);
                            } catch (AggregateNotFoundException e) {
                                logger.debug("Aggregate (with id: [{}]) cannot be loaded. "
                                                     + "Hence, message '[{}]' cannot be handled.",
                                             aggregateIdentifier, message);
                            }
                            return null;
                        },
                        invokerSegment,
                        CompletableFutureSegment,
                        new BlacklistDetectingCallback<>(
                                (commandMessage, commandResultMessage) -> {
                                    if (commandResultMessage.isExceptional()) {
                                        logger.warn("Failed sending message [{}] to aggregate with id [{}]",
                                                    message, aggregateIdentifier);
                                        future.completeExceptionally(commandResultMessage.exceptionResult());
                                    } else {
                                        future.complete(null);
                                    }
                                },
                                disruptor.getRingBuffer(),
                                (commandMessage, callback) -> send(message, scopeDescription, future),
                                rescheduleOnCorruptState
                        )
                );
            } finally {
                ringBuffer.publish(sequence);
            }
        }

        @Override
        public boolean canResolve(ScopeDescriptor scopeDescription) {
            return scopeDescription instanceof AggregateScopeDescriptor
                    && Objects.equals(type.getSimpleName(), ((AggregateScopeDescriptor) scopeDescription).getType());
        }
    }

    private class ExceptionHandler implements com.lmax.disruptor.ExceptionHandler<Object> {

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
