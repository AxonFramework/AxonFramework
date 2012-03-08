/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.ConflictingAggregateVersionException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Asynchronous CommandBus implementation with very high performance characteristics. It divides the command handling
 * process in three steps which can be executed in different threads. The CommandBus is backed by a {@link Disruptor},
 * which ensures that these steps are executed sequentially in these threads, while minimizing locking and inter-thread
 * communication.
 * <p/>
 * The process is split into three separate steps, each of which is executed in a different thread:
 * <ol>
 * <li><em>CommandHandler resolution and aggregate pre-loading</em><br/>
 * This process finds the Command Handler for an incoming command, prepares the interceptor chains and makes sure the
 * targeted aggregate is loaded into the cache.</li>
 * <li><em>Command Handler execution</em><br/>This process invokes the command handler with the incoming command. The
 * result and changes to the aggregate are recorded for the next step.</li>
 * <li><em>Event storage and publication</em><br/>This process stores all generated domain events and publishes them
 * (with any optional application events) to the event bus. Finally, an asynchronous task is scheduled to invoke the
 * command handler callback with the result of the command handling result.</li>
 * </ol>
 * <p/>
 * <em>Exceptions and recovery</em>
 * <p/>
 * This separation of process steps makes this implementation very efficient and highly performing. However, it does
 * not cope with exceptions very well. When an exception occurs, an Aggregate that has been pre-loaded is potentially
 * corrupt. That means that an aggregate does not represent a state that can be reproduced by replaying its committed
 * events. Although this implementation will recover from this corrupt state, it may result in a number of commands
 * being rejected in the meantime. These command may be retried. This is not done automatically.
 * <p/>
 * <em>Limitations of this implementation</em>
 * <p/>
 * Although this implementation allows applications to achieve extreme performance (over 1M commands on commodity
 * hardware), it does have some limitations. It currently only allows a single aggregate to be invoked during command
 * processing. Furthermore, the identifier of this aggregate must be made available in the command (see {@link
 * CommandTargetResolver}). Another limitation is that for each Aggregate Type, you will need to configure a separate
 * DisruptorCommandBus instance, as an instance is tied to a specific aggregate class.
 * <p/>
 * <em>Infrastructure considerations</em>
 * <p/>
 * This CommandBus implementation also implements {@link Repository} and requires this CommandBus to be injected as
 * repository into the CommandHandlers registered with this CommandBus. Using another repository will most likely
 * result in state changes being lost.
 * <p/>
 * The DisruptorCommandBus must have access to at least 4 threads, three of which are permanently used while the
 * DisruptorCommandBus is operational. At least on additional thread is required to invoke callbacks and initiate a
 * recovery process in the case of exceptions.
 * <p/>
 * Consider providing an alternative {@link org.axonframework.domain.IdentifierFactory} implementation. The default
 * implementation used {@link java.util.UUID#randomUUID()} to generated identifier for Events. The poor performance of
 * this method severely impacts overall performance of the DisruptorCommandBus. A better performing alternative is, for
 * example, <a href="http://johannburkard.de/software/uuid/" target="_blank"><code>com.eaio.uuid.UUID</code></a>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorCommandBus<T extends EventSourcedAggregateRoot> implements CommandBus, Repository<T> {

    private static final ThreadGroup DISRUPTOR_THREAD_GROUP = new ThreadGroup("DisruptorCommandBus");

    private final ConcurrentMap<Class<?>, CommandHandler<?>> commandHandlers = new ConcurrentHashMap<Class<?>, CommandHandler<?>>();
    private final Disruptor<CommandHandlingEntry<T>> disruptor;
    private final CommandHandlerInvoker<T> commandHandlerInvoker;
    private final ExecutorService executorService;
    private volatile boolean started = true;

    /**
     * Initialize the DisruptorCommandBus with given resources, using default configuration settings. Uses a Blocking
     * WaitStrategy on a RingBuffer of size 4096. The (3) Threads required for command execution are created
     * immediately. Additional threads are used to invoke response callbacks and to initialize a recovery process in
     * the case of errors.
     *
     * @param aggregateFactory      The factory providing uninitialized Aggregate instances for event sourcing
     * @param eventStore            The EventStore where generated events must be stored
     * @param eventBus              The EventBus where generated events must be published
     * @param commandTargetResolver The CommandTargetResolver that resolves the aggregate identifier for incoming
     *                              commands
     */
    public DisruptorCommandBus(AggregateFactory<T> aggregateFactory, EventStore eventStore, EventBus eventBus,
                               CommandTargetResolver commandTargetResolver) {
        this(aggregateFactory, eventStore, eventBus, commandTargetResolver, new DisruptorConfiguration());
    }

    /**
     * Initialize the DisruptorCommandBus with given resources and settings. The 3 Threads required for command
     * execution are immediately requested from the Configuration's Executor, if any. Otherwise, they are created.
     *
     * @param aggregateFactory      The factory providing uninitialized Aggregate instances for event sourcing
     * @param eventStore            The EventStore where generated events must be stored
     * @param eventBus              The EventBus where generated events must be published
     * @param configuration         The configuration for the command bus
     * @param commandTargetResolver The CommandTargetResolver that resolves the aggregate identifier for incoming
     *                              commands
     */
    @SuppressWarnings("unchecked")
    public DisruptorCommandBus(AggregateFactory<T> aggregateFactory, EventStore eventStore, EventBus eventBus,
                               CommandTargetResolver commandTargetResolver, DisruptorConfiguration configuration) {
        Executor executor = configuration.getExecutor();
        if (executor == null) {
            executorService = Executors.newCachedThreadPool(new AxonThreadFactory(DISRUPTOR_THREAD_GROUP));
            executor = executorService;
        } else {
            executorService = null;
        }
        disruptor = new Disruptor<CommandHandlingEntry<T>>(new CommandHandlingEntry.Factory<T>(),
                                                           executor,
                                                           configuration.getClaimStrategy(),
                                                           configuration.getWaitStrategy());
        commandHandlerInvoker = new CommandHandlerInvoker<T>();
        disruptor.handleEventsWith(new CommandHandlerPreFetcher<T>(eventStore,
                                                                   aggregateFactory,
                                                                   commandHandlers,
                                                                   configuration.getInvokerInterceptors(),
                                                                   configuration.getPublisherInterceptors(),
                                                                   commandTargetResolver))
                 .then(commandHandlerInvoker)
                 .then(new EventPublisher<T>(aggregateFactory.getTypeIdentifier(), eventStore,
                                             eventBus, executor, configuration.getRollbackConfiguration()));
        disruptor.start();
    }

    @Override
    public void dispatch(final CommandMessage<?> command) {
        dispatch(command, null);
    }

    @Override
    public <R> void dispatch(CommandMessage<?> command, CommandCallback<R> callback) {
        Assert.state(started, "CommandBus has been shut down. It is not accepting any Commands");
        RingBuffer<CommandHandlingEntry<T>> ringBuffer = disruptor.getRingBuffer();
        long sequence = ringBuffer.next();
        CommandHandlingEntry event = ringBuffer.get(sequence);
        event.reset(command, new BlacklistDetectingCallback<T, R>(callback, disruptor.getRingBuffer()));
        ringBuffer.publish(sequence);
    }

    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        commandHandlers.put(commandType, handler);
    }

    @Override
    public <C> boolean unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        return commandHandlers.remove(commandType, handler);
    }

    @Override
    public T load(Object aggregateIdentifier, Long expectedVersion) {
        T aggregate = load(aggregateIdentifier);
        if (expectedVersion != null && aggregate.getVersion() > expectedVersion) {
            throw new ConflictingAggregateVersionException(aggregateIdentifier,
                                                           expectedVersion,
                                                           aggregate.getVersion());
        }
        return aggregate;
    }

    @Override
    public T load(Object aggregateIdentifier) {
        T aggregateRoot = commandHandlerInvoker.getPreLoadedAggregate();
        if (aggregateRoot.getIdentifier().equals(aggregateIdentifier)) {
            return aggregateRoot;
        } else {
            throw new UnsupportedOperationException("Not supported to load another aggregate than the pre-loaded one");
        }
    }

    @Override
    public void add(T aggregate) {
        CurrentUnitOfWork.get().registerAggregate(aggregate, null, null);
    }

    /**
     * Shuts down the command bus. It no longer accepts new commands, and finishes processing commands that have
     * already been published. This method will not shut down any executor that has been provided as part of the
     * Configuration.
     */
    public void stop() {
        started = false;
        disruptor.shutdown();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private static class AxonThreadFactory implements ThreadFactory {

        private final ThreadGroup groupName;

        public AxonThreadFactory(ThreadGroup groupName) {
            this.groupName = groupName;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(groupName, r);
        }
    }
}
