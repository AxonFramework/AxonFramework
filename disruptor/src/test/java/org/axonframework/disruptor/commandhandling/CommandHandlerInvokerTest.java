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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.caching.Cache;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateCacheEntry;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandHandlerInvokerTest {

    private CommandHandlerInvoker testSubject;
    private EventStore mockEventStore;
    private Cache mockCache;
    private CommandHandlingEntry commandHandlingEntry;
    private String aggregateIdentifier;
    private CommandMessage<?> mockCommandMessage;
    private MessageHandler<CommandMessage<?>, CommandResultMessage<?>> mockCommandHandler;
    private SnapshotTriggerDefinition snapshotTriggerDefinition;
    private SnapshotTrigger mockTrigger;

    private static AtomicInteger messageHandlingCounter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
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
        messageHandlingCounter = new AtomicInteger(0);
    }

    @Test
    void usesProvidedParameterResolverFactoryToResolveParameters() {
        ParameterResolverFactory parameterResolverFactory =
                spy(ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        testSubject.createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                     snapshotTriggerDefinition, parameterResolverFactory);

        // The StubAggregate has three 'handle()' functions, hence verifying this 3 times
        verify(parameterResolverFactory, times(3)).createInstance(
                argThat(item -> "handle".equals(item.getName())), isA(Parameter[].class), anyInt()
        );
        verifyNoMoreInteractions(parameterResolverFactory);
    }

    @Test
    void loadFromRepositoryStoresLoadedAggregateInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handleSync(eq(mockCommandMessage)))
                .thenAnswer(invocationOnMock -> repository.load(aggregateIdentifier));
        when(mockEventStore.readEvents(any()))
                .thenReturn(DomainEventStream.of(
                        new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 0, aggregateIdentifier)
                ));
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).get(aggregateIdentifier);
        verify(mockCache).put(eq(aggregateIdentifier), notNull());
        verify(mockEventStore).readEvents(eq(aggregateIdentifier));
    }

    @Test
    void loadFromRepositoryLoadsFromCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handleSync(eq(mockCommandMessage)))
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
    void addToRepositoryAddsInCache() throws Exception {
        final Repository<StubAggregate> repository = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockCommandHandler.handleSync(eq(mockCommandMessage))).thenAnswer(invocationOnMock -> {
            Aggregate<StubAggregate> aggregate = repository.newInstance(() -> new StubAggregate(aggregateIdentifier));
            aggregate.execute(StubAggregate::doSomething);
            return aggregate.invoke(Function.identity());
        });

        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
        verify(mockEventStore).publish(ArgumentMatchers.<DomainEventMessage<?>[]>any());
    }

    @Test
    void cacheEntryInvalidatedOnRecoveryEntry() {
        commandHandlingEntry.resetAsRecoverEntry(aggregateIdentifier);
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).remove(aggregateIdentifier);
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
    }

    @Test
    void createRepositoryReturnsSameInstanceOnSecondInvocation() {
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

    @Test
    void canResolveReturnsTrueForMatchingAggregateDescriptor() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertTrue(testRepository.canResolve(new AggregateScopeDescriptor(
                StubAggregate.class.getSimpleName(), "some-identifier")
        ));
    }

    @Test
    void canResolveReturnsFalseNonAggregateScopeDescriptorImplementation() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertFalse(testRepository.canResolve(new SagaScopeDescriptor("some-saga-type", "some-identifier")));
    }

    @Test
    void canResolveReturnsFalseForNonMatchingAggregateType() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertFalse(testRepository.canResolve(
                new AggregateScopeDescriptor("other-non-matching-type", "some-identifier")
        ));
    }

    @Test
    void sendDeliversMessageAtDescribedAggregateInstance() throws Exception {
        String testAggregateId = "some-identifier";
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), testAggregateId);

        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockEventStore.readEvents(any()))
                .thenReturn(DomainEventStream.of(new GenericDomainEventMessage<>(
                        StubAggregate.class.getSimpleName(), testAggregateId, 0, testAggregateId
                )));

        commandHandlingEntry.start();
        try {
            testRepository.send(testMsg, testDescriptor);
        } finally {
            commandHandlingEntry.pause();
        }

        assertEquals(1, messageHandlingCounter.get());
    }

    private static class FailingPayload {

    }

    private static class DeadlinePayload {

    }

    @SuppressWarnings("unused")
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

        @DeadlineHandler
        public void handle(FailingPayload deadline) {
            throw new IllegalArgumentException();
        }

        @DeadlineHandler
        public void handle(DeadlinePayload deadline) {
            messageHandlingCounter.getAndIncrement();
        }

        @EventSourcingHandler
        public void handle(String id) {
            this.id = id;
        }
    }
}
