/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MetaData;
import org.junit.*;
import org.mockito.*;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executor;

import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvents;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
public class AbstractSnapshotterTest {

    private AbstractSnapshotter testSubject;
    private EventStore mockEventStore;
    private Logger logger;
    private Logger originalLogger;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        testSubject = TestSnapshotter.builder().eventStore(mockEventStore).build();
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
    public void testScheduleSnapshot_ConcurrencyExceptionIsSilenced() {
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

        testSubject = TestSnapshotter.builder()
                                     .eventStore(mockEventStore)
                                     .transactionManager(txManager)
                                     .build();

        testScheduleSnapshot();

        InOrder inOrder = inOrder(mockEventStore, txManager, mockTransaction);
        inOrder.verify(txManager).startTransaction();
        inOrder.verify(mockEventStore).readEvents(anyString());
        inOrder.verify(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));
        inOrder.verify(mockTransaction).commit();
    }

    private ArgumentMatcher<DomainEventMessage> event(final Object aggregateIdentifier, final long i) {
        return x -> aggregateIdentifier.equals(x.getAggregateIdentifier())
                && x.getSequenceNumber() == i;
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

    private static class TestSnapshotter extends AbstractSnapshotter {

        private TestSnapshotter(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
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

        private long getLastIdentifierFrom(DomainEventStream eventStream) {
            long lastSequenceNumber = -1;
            while (eventStream.hasNext()) {
                lastSequenceNumber = eventStream.next().getSequenceNumber();
            }
            return lastSequenceNumber;
        }

        private static class Builder extends AbstractSnapshotter.Builder {

            @Override
            public Builder eventStore(EventStore eventStore) {
                super.eventStore(eventStore);
                return this;
            }

            @Override
            public Builder executor(Executor executor) {
                super.executor(executor);
                return this;
            }

            @Override
            public Builder transactionManager(TransactionManager transactionManager) {
                super.transactionManager(transactionManager);
                return this;
            }

            private TestSnapshotter build() {
                return new TestSnapshotter(this);
            }
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
