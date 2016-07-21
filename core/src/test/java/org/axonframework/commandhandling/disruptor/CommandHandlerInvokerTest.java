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

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 */
public class CommandHandlerInvokerTest {

    private CommandHandlerInvoker testSubject;
    private EventStore mockEventStore;
    private Cache mockCache;
    private CommandHandlingEntry commandHandlingEntry;
    private String aggregateIdentifier;
    private CommandMessage<?> mockCommandMessage;
    private MessageHandler<CommandMessage<?>> mockCommandHandler;
    private EventStreamDecorator eventStreamDecorator;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        mockCache = mock(Cache.class);
        testSubject = new CommandHandlerInvoker(mockEventStore, mockCache, 0);
        aggregateIdentifier = "mockAggregate";
        mockCommandMessage = mock(CommandMessage.class);
        mockCommandHandler = mock(MessageHandler.class);
        commandHandlingEntry = new CommandHandlingEntry();
        commandHandlingEntry.reset(mockCommandMessage, mockCommandHandler, 0, 0, null,
                                   Collections.emptyList(),
                                   Collections.emptyList());
        eventStreamDecorator = mock(EventStreamDecorator.class);
        when(eventStreamDecorator.decorateForAppend(any(), any())).thenAnswer(new ReturnsArgumentAt(1));
        when(eventStreamDecorator.decorateForRead(any(), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLoadFromRepositoryStoresLoadedAggregateInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(new GenericAggregateFactory<>(StubAggregate.class), eventStreamDecorator);
        when(mockCommandHandler.handle(eq(mockCommandMessage)))
                .thenAnswer(invocationOnMock -> repository.load(aggregateIdentifier));
        when(mockEventStore.readEvents(anyObject())).thenReturn(DomainEventStream
                                                                        .of(new GenericDomainEventMessage<>("type",
                                                                                                            aggregateIdentifier,
                                                                                                            0,
                                                                                                            aggregateIdentifier)));
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).get(aggregateIdentifier);
        verify(mockCache).put(eq(aggregateIdentifier), isA(EventSourcedAggregate.class));
        verify(mockEventStore).readEvents(eq(aggregateIdentifier));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLoadFromRepositoryLoadsFromCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(new GenericAggregateFactory<>(StubAggregate.class), eventStreamDecorator);
        when(mockCommandHandler.handle(eq(mockCommandMessage)))
                .thenAnswer(invocationOnMock -> repository.load(aggregateIdentifier));
        when(mockCache.get(aggregateIdentifier)).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return EventSourcedAggregate.initialize(new StubAggregate(aggregateIdentifier),
                                                        ModelInspector.inspectAggregate(StubAggregate.class),
                                                        mockEventStore);
            }
        });
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).get(aggregateIdentifier);
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddToRepositoryAddsInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(new GenericAggregateFactory<>(StubAggregate.class), eventStreamDecorator);
        when(mockCommandHandler.handle(eq(mockCommandMessage))).thenAnswer(invocationOnMock -> {
            Aggregate<StubAggregate> aggregate = repository.newInstance(() -> new StubAggregate(aggregateIdentifier));
            aggregate.execute(StubAggregate::doSomething);
            return aggregate.invoke(Function.identity());
        });

        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).put(eq(aggregateIdentifier), isA(EventSourcedAggregate.class));
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
        verify(mockEventStore).publish(Matchers.<DomainEventMessage<?>[]>anyVararg());
    }

    @Test
    public void testCacheEntryInvalidatedOnRecoveryEntry() throws Exception {
        commandHandlingEntry.resetAsRecoverEntry(aggregateIdentifier);
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).remove(aggregateIdentifier);
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
    }

    @Test
    public void testCreateRepositoryReturnsSameInstanceOnSecondInvocation() {
        final Repository<StubAggregate> repository1 = testSubject
                .createRepository(new GenericAggregateFactory<>(StubAggregate.class), eventStreamDecorator);
        final Repository<StubAggregate> repository2 = testSubject
                .createRepository(new GenericAggregateFactory<>(StubAggregate.class), eventStreamDecorator);

        assertSame(repository1, repository2);
    }

    public static class StubAggregate {

        @AggregateIdentifier
        private String id;

        public StubAggregate() {
        }

        public StubAggregate(String id) {
            this.id = id;
        }

        public void doSomething() {
            apply(id);
        }

        @EventSourcingHandler
        public void handle(String id) {
            this.id = id;
        }

    }

}
