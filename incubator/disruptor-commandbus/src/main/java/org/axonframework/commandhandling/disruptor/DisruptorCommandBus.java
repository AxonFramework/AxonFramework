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

import com.lmax.disruptor.ClaimStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.wizard.DisruptorWizard;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.domain.AggregateIdentifier;
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
    private final RingBuffer<CommandHandlingEntry> ringBuffer;

    //    private final Consumer eventPublisher;
    private ConcurrentMap<Class<?>, CommandHandler<?>> commandHandlers = new ConcurrentHashMap<Class<?>, CommandHandler<?>>();
    private DisruptorWizard<CommandHandlingEntry> wizard;

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
        wizard = new DisruptorWizard<CommandHandlingEntry>(new CommandHandlingEntryFactory(),
                                                           bufferSize,
                                                           executor,
                                                           ClaimStrategy.Option.SINGLE_THREADED,
                                                           WaitStrategy.Option.YIELDING);


        //noinspection unchecked
        wizard.handleEventsWith(new CommandHandlerPreFetcher(eventStore, aggregateFactory, commandHandlers))
              .then(new CommandHandlerInvoker())
              .then(new EventPublisher(eventStore,
                                       aggregateFactory.getTypeIdentifier(),
                                       eventBus));
        ringBuffer = wizard.start();
    }

    @Override
    public void dispatch(Object command) {
        CommandHandlingEntry entry = ringBuffer.nextEvent();
        entry.setCommand(command);
        entry.setAggregateIdentifier(((IdentifiedCommand) command).getAggregateIdentifier());
        ringBuffer.publish(entry);
    }

    @Override
    public <R> void dispatch(Object command, CommandCallback<R> callback) {
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
    public AggregateRoot load(AggregateIdentifier aggregateIdentifier, Long expectedVersion) {
        return load(aggregateIdentifier);
    }

    @Override
    public AggregateRoot load(AggregateIdentifier aggregateIdentifier) {

        //        CurrentUnitOfWork.get().registerAggregate(aggregateRoot, null);
//        if (aggregateRoot.getIdentifier().equals(aggregateIdentifier)) {
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
        wizard.halt();
    }

    private static class CommandHandlingEntryFactory implements EventFactory<CommandHandlingEntry> {
        @Override
        public CommandHandlingEntry create() {
            return new CommandHandlingEntry();
        }
    }
}
