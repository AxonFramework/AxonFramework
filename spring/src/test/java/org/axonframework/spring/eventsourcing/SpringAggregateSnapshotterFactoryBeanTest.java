/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.spring.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AbstractAggregateFactory;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MetaData;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.spring.config.annotation.StubAggregate;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class SpringAggregateSnapshotterFactoryBeanTest {

    private SpringAggregateSnapshotterFactoryBean testSubject;
    private PlatformTransactionManager mockTransactionManager;
    private String aggregateIdentifier;
    private EventStore mockEventStore;
    private RepositoryProvider mockRepositoryProvider;
    private ApplicationContext mockApplicationContext;
    private Executor executor;

    @BeforeEach
    void setUp() {
        mockApplicationContext = mock(ApplicationContext.class);
        mockEventStore = mock(EventStore.class);
        mockRepositoryProvider = mock(RepositoryProvider.class);
        executor = spy(new MockExecutor());

        testSubject = new SpringAggregateSnapshotterFactoryBean();
        testSubject.setApplicationContext(mockApplicationContext);
        testSubject.setExecutor(executor);
        when(mockApplicationContext.getBeansOfType(AggregateFactory.class)).thenReturn(
                Collections.singletonMap("myFactory",
                                         new AbstractAggregateFactory<StubAggregate>(StubAggregate.class) {
                                             @Override
                                             public StubAggregate doCreateAggregate(String aggregateIdentifier,
                                                                                    DomainEventMessage firstEvent) {
                                                 return new StubAggregate(aggregateIdentifier);
                                             }
                                         }));
        testSubject.setEventStore(mockEventStore);
        testSubject.setRepositoryProvider(mockRepositoryProvider);
        mockTransactionManager = mock(PlatformTransactionManager.class);
        aggregateIdentifier = UUID.randomUUID().toString();

        String type = "testAggregate";
        DomainEventMessage event1 = new GenericDomainEventMessage<>(type, aggregateIdentifier, 0L, "Mock contents",
                                                                    MetaData.emptyInstance());
        DomainEventMessage event2 = new GenericDomainEventMessage<>(type, aggregateIdentifier, 1L, "Mock contents",
                                                                    MetaData.emptyInstance());
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(event1, event2));
    }

    @Test
    void snapshotCreatedNoTransaction() {
        SpringAggregateSnapshotter snapshotter = testSubject.getObject();
        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStore).storeSnapshot(eventSequence(1L));
        verify(executor).execute(any());
    }

    @Test
    void retrieveAggregateFactoryFromRepositoryIfNotExplicitlyAvailable() {
        testSubject.setEventStore(null);
        reset(mockApplicationContext);
        when(mockApplicationContext.getBean(EventStore.class)).thenReturn(mockEventStore);

        when(mockApplicationContext.getBeansOfType(EventSourcingRepository.class))
                .thenReturn(Collections.singletonMap("myRepository",
                                                     EventSourcingRepository.builder(StubAggregate.class)
                                                                            .eventStore(mockEventStore)
                                                                            .repositoryProvider(mockRepositoryProvider)
                                                                            .build()
                ));
        snapshotCreatedNoTransaction();
    }

    @Test
    void snapshotCreatedNewlyCreatedTransactionCommitted() {
        testSubject.setTransactionManager(mockTransactionManager);
        SpringAggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus newlyCreatedTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(newlyCreatedTransaction);

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStore).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager).commit(newlyCreatedTransaction);
    }

    @Test
    void snapshotCreatedExistingTransactionNotCommitted() {
        testSubject.setTransactionManager(mockTransactionManager);
        SpringAggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class))).thenReturn(existingTransaction);

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStore).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
    }

    @Test
    void snapshotCreatedExistingTransactionNotRolledBack() {
        testSubject.setTransactionManager(mockTransactionManager);
        SpringAggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class))).thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub")).when(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStore).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
        verify(mockTransactionManager, never()).rollback(existingTransaction);
    }

    @Test
    void snapshotCreatedNewTransactionRolledBack() {
        testSubject.setTransactionManager(mockTransactionManager);
        SpringAggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(any())).thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub")).when(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStore).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
        verify(mockTransactionManager).rollback(existingTransaction);
    }

    private DomainEventMessage eventSequence(final long sequenceNumber) {
        return argThat(o -> o != null &&
                o.getSequenceNumber() == sequenceNumber);
    }

    public static class MockExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
