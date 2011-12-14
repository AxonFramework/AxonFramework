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

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;
import org.axonframework.serializer.Serializer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBus implements CommandBus, Repository {

    private static final ThreadGroup DISRUPTOR_THREAD_GROUP = new ThreadGroup("Disruptor");

    private final ConcurrentMap<Class<?>, CommandHandler<?>> commandHandlers = new ConcurrentHashMap<Class<?>, CommandHandler<?>>();
    private final Disruptor<CommandHandlingEntry> disruptor;

    public DisruptorCommandBus(int bufferSize, AggregateFactory<?> eventStore, EventStore aggregateFactory,
                               EventBus eventBus) {
        this(Executors.defaultThreadFactory(), bufferSize, eventStore, aggregateFactory, eventBus, null);
    }

    public DisruptorCommandBus(int bufferSize, AggregateFactory<?> eventStore, EventStore aggregateFactory,
                               EventBus eventBus, Serializer eventSerializer) {
        this(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(DISRUPTOR_THREAD_GROUP, r);
            }
        }, bufferSize, eventStore, aggregateFactory, eventBus, eventSerializer);
    }

    public DisruptorCommandBus(final ThreadFactory threadFactory, int bufferSize, AggregateFactory<?> aggregateFactory,
                               EventStore eventStore,
                               EventBus eventBus, Serializer eventSerializer) {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                threadFactory.newThread(command).start();
            }
        };
        disruptor = new Disruptor<CommandHandlingEntry>(new CommandHandlingEntryFactory(),
                                                        executor,
                                                        new MultiThreadedClaimStrategy(bufferSize),
                                                        new YieldingWaitStrategy());


        //noinspection unchecked
        disruptor.handleEventsWith(new CommandHandlerPreFetcher(eventStore, aggregateFactory, commandHandlers))
                 .then(new CommandHandlerInvoker())
                 .then(new EventPublisher(eventStore,
                                          aggregateFactory.getTypeIdentifier(),
                                          eventBus));
        disruptor.start();
    }

    @Override
    public void dispatch(final CommandMessage<?> command) {
        RingBuffer<CommandHandlingEntry> ringBuffer = disruptor.getRingBuffer();
        Object aggregateIdentifier = ((IdentifiedCommand) command.getPayload()).getAggregateIdentifier();
        long sequence = ringBuffer.next();
        CommandHandlingEntry event = ringBuffer.get(sequence);
        event.clear(command, aggregateIdentifier);
        ringBuffer.publish(sequence);
    }

    @Override
    public <R> void dispatch(CommandMessage<?> command, CommandCallback<R> callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        commandHandlers.put(commandType, handler);
    }

    @Override
    public <C> void unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        commandHandlers.remove(commandType, handler);
    }

    @Override
    public AggregateRoot load(Object aggregateIdentifier, Long expectedVersion) {
        return load(aggregateIdentifier);
    }

    @Override
    public AggregateRoot load(Object aggregateIdentifier) {

        //        CurrentUnitOfWork.get().registerAggregate(aggregateRoot, null);
//        if (aggregateRoot.id().equals(aggregateIdentifier)) {
        return CommandHandlerInvoker.preLoadedAggregate.get();
//        } else {
//            throw new UnsupportedOperationException("Not supported to load another aggregate than the pre-loaded one");
//        }
    }

    @Override
    public void add(AggregateRoot aggregate) {
        // not necessary
    }

    public void stop() {
        disruptor.shutdown();
    }

    private static class CommandHandlingEntryFactory implements EventFactory<CommandHandlingEntry> {
        @Override
        public CommandHandlingEntry newInstance() {
            return new CommandHandlingEntry();
        }
    }
}
