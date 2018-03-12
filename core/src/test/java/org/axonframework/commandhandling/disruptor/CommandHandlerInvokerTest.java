/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.function.Function;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

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
    private SnapshotTriggerDefinition snapshotTriggerDefinition;
    private SnapshotTrigger mockTrigger;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        mockCache = mock(Cache.class);
        doAnswer(invocation -> {
            // attempt to serialize whatever is being added to the cache
            try {
                new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(invocation.getArguments()[1]);
            } catch (Exception e) {
                fail("Attempt to add a non-serializable instance to the cache: " + invocation.getArgument(1));
            }
            return null;
        }).when(mockCache).put(anyString(), any());
        testSubject = new CommandHandlerInvoker(mockCache, 0);
        aggregateIdentifier = "mockAggregate";
        mockCommandMessage = mock(CommandMessage.class);
        mockCommandHandler = mock(MessageHandler.class);
        commandHandlingEntry = new CommandHandlingEntry();
        commandHandlingEntry.reset(mockCommandMessage, mockCommandHandler, 0, 0, null,
                                   Collections.emptyList(),
                                   Collections.emptyList());
        mockTrigger = mock(SnapshotTrigger.class);
        snapshotTriggerDefinition = mock(SnapshotTriggerDefinition.class);
        when(snapshotTriggerDefinition.prepareTrigger(any())).thenReturn(mockTrigger);
    }

    @Test
    public void usesProvidedParameterResolverFactoryToResolveParameters() throws Exception {
        ParameterResolverFactory parameterResolverFactory = spy(ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        testSubject.createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                     snapshotTriggerDefinition, parameterResolverFactory);

        verify(parameterResolverFactory).createInstance(argThat(item -> "handle".equals(item.getName())), isA(Parameter[].class), anyInt());
        verifyNoMoreInteractions(parameterResolverFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLoadFromRepositoryStoresLoadedAggregateInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handle(eq(mockCommandMessage)))
                .thenAnswer(invocationOnMock -> repository.load(aggregateIdentifier));
        when(mockEventStore.readEvents(anyObject())).thenReturn(DomainEventStream
                                                                        .of(new GenericDomainEventMessage<>("type",
                                                                                                            aggregateIdentifier,
                                                                                                            0,
                                                                                                            aggregateIdentifier)));
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).get(aggregateIdentifier);
        verify(mockCache).put(eq(aggregateIdentifier), notNull());
        verify(mockEventStore).readEvents(eq(aggregateIdentifier));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLoadFromRepositoryLoadsFromCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handle(eq(mockCommandMessage)))
                .thenAnswer(invocationOnMock -> repository.load(aggregateIdentifier));
        when(mockCache.get(aggregateIdentifier)).thenAnswer(
                invocationOnMock -> new AggregateCacheEntry<>(
                        EventSourcedAggregate.initialize(new StubAggregate(aggregateIdentifier),
                                                         AnnotatedAggregateMetaModelFactory.inspectAggregate(StubAggregate.class),
                                                         mockEventStore, mockTrigger)));
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).get(aggregateIdentifier);
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddToRepositoryAddsInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handle(eq(mockCommandMessage))).thenAnswer(invocationOnMock -> {
            Aggregate<StubAggregate> aggregate = repository.newInstance(() -> new StubAggregate(aggregateIdentifier));
            aggregate.execute(StubAggregate::doSomething);
            return aggregate.invoke(Function.identity());
        });

        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
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
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        final Repository<StubAggregate> repository2 = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertSame(repository1, repository2);
    }

    public static class StubAggregate implements Serializable {

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
