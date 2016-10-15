/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventstore.SnapshotEventStore;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;
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
public class SpringAggregateSnapshotterTest {

    private SpringAggregateSnapshotter testSubject;
    private ApplicationContext mockApplicationContext;
    private PlatformTransactionManager mockTransactionManager;
    private UUID aggregateIdentifier;
    private SnapshotEventStore mockEventStore;

    @Before
    public void setUp() throws Exception {
        mockApplicationContext = mock(ApplicationContext.class);
        mockEventStore = mock(SnapshotEventStore.class);

        testSubject = new SpringAggregateSnapshotter();
        testSubject.setApplicationContext(mockApplicationContext);
        when(mockApplicationContext.getBeansOfType(AggregateFactory.class)).thenReturn(
                Collections.<String, AggregateFactory>singletonMap(
                        "myFactory",
                        new AbstractAggregateFactory<StubAggregate>() {
                            @Override
                            public StubAggregate doCreateAggregate(Object aggregateIdentifier,
                                                                   DomainEventMessage firstEvent) {
                                return new StubAggregate(aggregateIdentifier);
                            }

                            @Override
                            public String getTypeIdentifier() {
                                return "stub";
                            }

                            @Override
                            public Class<StubAggregate> getAggregateType() {
                                return StubAggregate.class;
                            }
                        }));
        testSubject.setEventStore(mockEventStore);
        testSubject.start();
        mockTransactionManager = mock(PlatformTransactionManager.class);
        aggregateIdentifier = UUID.randomUUID();

        DomainEventMessage event1 = new GenericDomainEventMessage<String>(aggregateIdentifier, 0L,
                                                                          "Mock contents", MetaData.emptyInstance());
        DomainEventMessage event2 = new GenericDomainEventMessage<String>(aggregateIdentifier, 1L,
                                                                          "Mock contents", MetaData.emptyInstance());
        when(mockEventStore.readEvents("stub", aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(event1, event2));
    }

    @Test
    public void testSnapshotCreated_NoTransaction() {
        testSubject.scheduleSnapshot("stub", aggregateIdentifier);

        verify(mockEventStore).appendSnapshotEvent(eq("stub"), eventSequence(1L));
    }

    @Test
    public void testSnapshotCreated_NewlyCreatedTransactionCommitted() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        testSubject.start();
        SimpleTransactionStatus newlyCreatedTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(newlyCreatedTransaction);

        testSubject.scheduleSnapshot("stub", aggregateIdentifier);

        verify(mockEventStore).appendSnapshotEvent(eq("stub"), eventSequence(1L));
        verify(mockTransactionManager).commit(newlyCreatedTransaction);
    }

    @Test
    public void testSnapshotCreated_ExistingTransactionNotCommitted() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        testSubject.start();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);

        testSubject.scheduleSnapshot("stub", aggregateIdentifier);

        verify(mockEventStore).appendSnapshotEvent(eq("stub"), eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
    }

    @Test
    public void testSnapshotCreated_ExistingTransactionNotRolledBack() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        testSubject.start();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(false);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub"))
                .when(mockEventStore).appendSnapshotEvent(isA(String.class), isA(DomainEventMessage.class));

        testSubject.scheduleSnapshot("stub", aggregateIdentifier);

        verify(mockEventStore).appendSnapshotEvent(eq("stub"), eventSequence(1L));
        verify(mockTransactionManager, never()).commit(existingTransaction);
        verify(mockTransactionManager, never()).rollback(existingTransaction);
    }

    @Test
    public void testSnapshotCreated_NewTransactionRolledBack() throws Exception {
        testSubject.setTransactionManager(mockTransactionManager);
        testSubject.start();
        SimpleTransactionStatus existingTransaction = new SimpleTransactionStatus(true);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(existingTransaction);
        doThrow(new RuntimeException("Stub"))
                .when(mockEventStore).appendSnapshotEvent(isA(String.class), isA(DomainEventMessage.class));

        testSubject.scheduleSnapshot("stub", aggregateIdentifier);

        verify(mockEventStore).appendSnapshotEvent(eq("stub"), eventSequence(1L));
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
