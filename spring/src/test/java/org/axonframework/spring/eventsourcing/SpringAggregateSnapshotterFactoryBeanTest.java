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

package org.axonframework.spring.eventsourcing;

import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.spring.config.annotation.StubAggregate;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SpringAggregateSnapshotterFactoryBeanTest {

    private SpringAggregateSnapshotterFactoryBean testSubject;
    private PlatformTransactionManager mockTransactionManager;
    private String aggregateIdentifier;
    private EventStorageEngine mockEventStorage;
    private String type = "testAggregate";

    @Before
    public void setUp() throws Exception {
        ApplicationContext mockApplicationContext = mock(ApplicationContext.class);
        mockEventStorage = mock(EventStorageEngine.class);

        testSubject = new SpringAggregateSnapshotterFactoryBean();
        testSubject.setApplicationContext(mockApplicationContext);
        when(mockApplicationContext.getBeansOfType(AggregateFactory.class)).thenReturn(
                Collections.<String, AggregateFactory>singletonMap(
                        "myFactory",
                        new AbstractAggregateFactory<StubAggregate>(StubAggregate.class) {
                            @Override
                            public StubAggregate doCreateAggregate(String aggregateIdentifier,
                                                                   DomainEventMessage firstEvent) {
                                return new StubAggregate(aggregateIdentifier);
                            }
                        }));
        testSubject.setEventStorageEngine(mockEventStorage);
        mockTransactionManager = mock(PlatformTransactionManager.class);
        aggregateIdentifier = UUID.randomUUID().toString();

        DomainEventMessage event1 = new GenericDomainEventMessage<>(type, aggregateIdentifier, 0L,
                                                                          "Mock contents", MetaData.emptyInstance());
        DomainEventMessage event2 = new GenericDomainEventMessage<>(type, aggregateIdentifier, 1L,
                                                                          "Mock contents", MetaData.emptyInstance());
        when(mockEventStorage.readEvents(aggregateIdentifier))
                .thenReturn(DomainEventStream.of(event1, event2));
    }

    @Test
    public void testSnapshotCreated_NoTransaction() throws Exception {
        testSubject.getObject().scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStorage).storeSnapshot(eventSequence(1L));
    }

    @Test
    public void testSnapshotCreated_NewlyCreatedTransactionCommitted() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        AggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus newlyCreatedTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(newlyCreatedTransaction);

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStorage).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager).commit(newlyCreatedTransaction);
    }

    @Test
    public void testSnapshotCreated_ExistingTransactionNotCommitted() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        AggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStorage).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
    }

    @Test
    public void testSnapshotCreated_ExistingTransactionNotRolledBack() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        AggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub"))
                .when(mockEventStorage).storeSnapshot(isA(DomainEventMessage.class));

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStorage).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
        verify(mockTransactionManager, never()).rollback(existingTransaction);
    }

    @Test
    public void testSnapshotCreated_NewTransactionRolledBack() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        AggregateSnapshotter snapshotter = testSubject.getObject();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub"))
                .when(mockEventStorage).storeSnapshot(isA(DomainEventMessage.class));

        snapshotter.scheduleSnapshot(StubAggregate.class, aggregateIdentifier);

        verify(mockEventStorage).storeSnapshot(eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
        verify(mockTransactionManager).rollback(existingTransaction);
    }

    private DomainEventMessage eventSequence(final long sequenceNumber) {
        return argThat(new BaseMatcher<DomainEventMessage>() {
            @Override
            public boolean matches(Object o) {
                return o instanceof DomainEventMessage
                        && ((DomainEventMessage) o).getSequenceNumber() == sequenceNumber;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("expected event with sequence number: ");
                description.appendValue(sequenceNumber);
            }
        });
    }
}
