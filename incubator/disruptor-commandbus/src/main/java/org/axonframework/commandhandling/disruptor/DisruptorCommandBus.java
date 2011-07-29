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

import com.lmax.disruptor.BatchConsumer;
import com.lmax.disruptor.ClaimStrategy;
import com.lmax.disruptor.Consumer;
import com.lmax.disruptor.ConsumerBarrier;
import com.lmax.disruptor.EntryFactory;
import com.lmax.disruptor.ProducerBarrier;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBus implements CommandBus, Repository {

    private final RingBuffer<CommandHandlingEntry> ringBuffer;

    private final Consumer aggregatePreLoader;
    private final Consumer commandHandlerResolver;
    private final Consumer commandHandlerInvoker;
    private final Consumer eventStoreAppender;
    private final Consumer eventPublisher;
    private final ProducerBarrier<CommandHandlingEntry> producerBarrier;

    public DisruptorCommandBus(int bufferSize, AggregateFactory<?> eventStore, EventStore aggregateFactory,
                               Map<Class<?>, CommandHandler<?>> commandHandlers, EventBus eventBus) {
        this(Executors.defaultThreadFactory(), bufferSize, eventStore, aggregateFactory, commandHandlers, eventBus);
    }

    public DisruptorCommandBus(ThreadFactory threadFactory, int bufferSize, AggregateFactory<?> aggregateFactory,
                               EventStore eventStore, Map<Class<?>, CommandHandler<?>> commandHandlers,
                               EventBus eventBus) {
        this.ringBuffer = new RingBuffer<CommandHandlingEntry>(new CommandHandlingEntryFactory(),
                                                               bufferSize,
                                                               ClaimStrategy.Option.SINGLE_THREADED,
                                                               WaitStrategy.Option.YIELDING);
        ConsumerBarrier<CommandHandlingEntry> consumerBarrier1 = ringBuffer.createConsumerBarrier();
        aggregatePreLoader = new BatchConsumer<CommandHandlingEntry>(consumerBarrier1,
                                                                     new AggregatePreLoader(eventStore,
                                                                                            aggregateFactory));
        commandHandlerResolver = new BatchConsumer<CommandHandlingEntry>(consumerBarrier1,
                                                                         new CommandHandlerResolver(commandHandlers));
        ConsumerBarrier<CommandHandlingEntry> consumerBarrier2 =
                ringBuffer.createConsumerBarrier(aggregatePreLoader, commandHandlerResolver);
        commandHandlerInvoker = new BatchConsumer<CommandHandlingEntry>(consumerBarrier2, new CommandHandlerInvoker());

        ConsumerBarrier<CommandHandlingEntry> consumerBarrier3 = ringBuffer
                .createConsumerBarrier(commandHandlerInvoker);
        eventStoreAppender = new BatchConsumer<CommandHandlingEntry>(
                consumerBarrier3,
                new EventStoreAppender(eventStore, aggregateFactory.getTypeIdentifier()));

        ConsumerBarrier<CommandHandlingEntry> consumerBarrier4 = ringBuffer.createConsumerBarrier(eventStoreAppender);
        eventPublisher = new BatchConsumer<CommandHandlingEntry>(consumerBarrier4,
                                                                 new EventPublishingHandler(eventBus));

        this.producerBarrier = ringBuffer.createProducerBarrier(eventPublisher);

        threadFactory.newThread(aggregatePreLoader).start();
        threadFactory.newThread(commandHandlerInvoker).start();
        threadFactory.newThread(commandHandlerResolver).start();
        threadFactory.newThread(eventPublisher).start();
        threadFactory.newThread(eventStoreAppender).start();
    }

    @Override
    public void dispatch(Object command) {
        CommandHandlingEntry entry = producerBarrier.nextEntry();
        entry.setCommand(command);
        entry.setAggregateIdentifier(((IdentifiedCommand) command).getAggregateIdentifier());
        producerBarrier.commit(entry);
    }

    @Override
    public <R> void dispatch(Object command, CommandCallback<R> callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C> void subscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public <C> void unsubscribe(Class<C> commandType, CommandHandler<? super C> handler) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public AggregateRoot load(AggregateIdentifier aggregateIdentifier, Long expectedVersion) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public AggregateRoot load(AggregateIdentifier aggregateIdentifier) {

        EventSourcedAggregateRoot aggregateRoot = CommandHandlerInvoker.preLoadedAggregate.get();
//        CurrentUnitOfWork.get().registerAggregate(aggregateRoot, null);
        if (aggregateRoot.getIdentifier().equals(aggregateIdentifier)) {
            return aggregateRoot;
        } else {
            throw new UnsupportedOperationException("Not supported to load another aggregate than the pre-loaded one");
        }
    }

    @Override
    public void add(AggregateRoot aggregate) {
        // not necessary
    }

    public void stop() {
        aggregatePreLoader.halt();
        commandHandlerInvoker.halt();
        commandHandlerResolver.halt();
        eventPublisher.halt();
        eventStoreAppender.halt();
    }

    private static class CommandHandlingEntryFactory implements EntryFactory<CommandHandlingEntry> {
        @Override
        public CommandHandlingEntry create() {
            return new CommandHandlingEntry();
        }
    }
}
