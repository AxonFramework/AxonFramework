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

package org.axonframework.eventsourcing;

import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MetaData;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AbstractSnapshotterTest {

    private AbstractSnapshotter testSubject;
    private EventStore mockEventStore;
    private Logger logger;
    private Logger originalLogger;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        testSubject = new TestSnapshotter(mockEventStore);
        logger = mock(Logger.class);
        originalLogger = replaceLogger(logger);
    }

    @After
    public void tearDown() throws Exception {
        if (originalLogger != null) {
            replaceLogger(originalLogger);
        }
    }

    @Test
    public void testScheduleSnapshot() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    public void testScheduleSnapshot_ConcurrencyExceptionIsSilenced()
            throws NoSuchFieldException, IllegalAccessException {
        final String aggregateIdentifier = "aggregateIdentifier";
        doNothing()
                .doThrow(new ConcurrencyException("Mock"))
                .when(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenAnswer(invocationOnMock -> DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);

        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, times(2)).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
        verify(logger, never()).warn(anyString());
        verify(logger, never()).error(anyString());
    }

    @Test
    public void testScheduleSnapshot_SnapshotIsNull() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent()));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @Test
    public void testScheduleSnapshot_SnapshotReplacesOneEvent() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testScheduleSnapshot_WithTransaction() {
        Transaction mockTransaction = mock(Transaction.class);
        TransactionManager txManager = spy(new StubTransactionManager(mockTransaction));
        when(txManager.startTransaction()).thenReturn(mockTransaction);

        testSubject = new TestSnapshotter(mockEventStore, txManager);

        testScheduleSnapshot();

        InOrder inOrder = inOrder(mockEventStore, txManager, mockTransaction);
        inOrder.verify(txManager).startTransaction();
        inOrder.verify(mockEventStore).readEvents(anyString());
        inOrder.verify(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));
        inOrder.verify(mockTransaction).commit();
    }

    private Matcher<DomainEventMessage> event(final Object aggregateIdentifier, final long i) {
        return new ArgumentMatcher<DomainEventMessage>() {
            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof DomainEventMessage)) {
                    return false;
                }
                DomainEventMessage event = (DomainEventMessage) argument;
                return aggregateIdentifier.equals(event.getAggregateIdentifier())
                        && event.getSequenceNumber() == i;
            }
        };
    }

    private long getLastIdentifierFrom(DomainEventStream eventStream) {
        long lastSequenceNumber = -1;
        while (eventStream.hasNext()) {
            lastSequenceNumber = eventStream.next().getSequenceNumber();
        }
        return lastSequenceNumber;
    }

    private Logger replaceLogger(Logger mockLogger) throws NoSuchFieldException, IllegalAccessException {
        Field loggerField = AbstractSnapshotter.class.getDeclaredField("logger");
        ReflectionUtils.ensureAccessible(loggerField);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(loggerField, loggerField.getModifiers() & ~Modifier.FINAL);
        Logger originalLogger = (Logger) loggerField.get(null);
        loggerField.set(null, mockLogger);
        return originalLogger;
    }

    private class TestSnapshotter extends AbstractSnapshotter {
        public TestSnapshotter(EventStore eventStore) {
            super(eventStore);
        }

        public TestSnapshotter(EventStore eventStore, TransactionManager transactionManager) {
            super(eventStore, DirectExecutor.INSTANCE, transactionManager);
        }

        @Override
        protected DomainEventMessage createSnapshot(Class<?> aggregateType,
                                                    String aggregateIdentifier, DomainEventStream eventStream) {
            long lastIdentifier = getLastIdentifierFrom(eventStream);
            if (lastIdentifier <= 0) {
                return null;
            }
            return new GenericDomainEventMessage<>("test", aggregateIdentifier, lastIdentifier,
                                                   "Mock contents", MetaData.emptyInstance());
        }
    }

    private static class StubTransactionManager implements TransactionManager {

        private final Transaction transaction;

        private StubTransactionManager(Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public Transaction startTransaction() {
            return transaction;
        }
    }
}
