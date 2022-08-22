/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class AbstractSnapshotterTest {

    private AbstractSnapshotter testSubject;
    private EventStore mockEventStore;
    private Logger logger;
    private Logger originalLogger;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        spanFactory = new TestSpanFactory();
        testSubject = TestSnapshotter.builder()
                                     .eventStore(mockEventStore)
                                     .spanFactory(spanFactory)
                                     .build();
        logger = mock(Logger.class);
        originalLogger = replaceLogger(logger);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (originalLogger != null) {
            replaceLogger(originalLogger);
        }
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void testScheduleSnapshot() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    void testSnapshotTracing() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanActive("TestSnapshotter.createSnapshot(Object)");
                    spanFactory.verifySpanActive("TestSnapshotter.createSnapshot(Object,aggregateIdentifier)");
                    return DomainEventStream.of(createEvents(2));
                });
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
        spanFactory.verifySpanCompleted("TestSnapshotter.createSnapshot(Object)");
        spanFactory.verifySpanCompleted("TestSnapshotter.createSnapshot(Object,aggregateIdentifier)");
        spanFactory.verifySpanHasType("TestSnapshotter.createSnapshot(Object)", TestSpanFactory.TestSpanType.ROOT);
        spanFactory.verifySpanHasType("TestSnapshotter.createSnapshot(Object,aggregateIdentifier)", TestSpanFactory.TestSpanType.INTERNAL);
    }

    @Test
    void testScheduleSnapshotIsPostponedUntilUnitOfWorkAfterCommit() {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(argThat(event(aggregateIdentifier, 1)));

        uow.commit();

        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    void testScheduleSnapshotOnlyOnce() {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(argThat(event(aggregateIdentifier, 1)));

        uow.commit();

        verify(mockEventStore, times(1)).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    void testScheduleSnapshot_ConcurrencyExceptionIsSilenced() {
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
    void testScheduleSnapshot_SnapshotIsNull() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent()));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @Test
    void testScheduleSnapshot_SnapshotReplacesOneEvent() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testScheduleSnapshot_WithTransaction() {
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

    @Test
    void testScheduleSnapshot_IgnoredWhenSnapshotAlreadyScheduled() {
        StubExecutor executor = new StubExecutor();
        testSubject = TestSnapshotter.builder().eventStore(mockEventStore).executor(executor).build();

        testSubject.scheduleSnapshot(Object.class, "id1");
        testSubject.scheduleSnapshot(Object.class, "id1");

        assertEquals(1, executor.size());
        executor.executeNext();
        assertEquals(0, executor.size());

        testSubject.scheduleSnapshot(Object.class, "id1");

        assertEquals(1, executor.size());
    }

    @Test
    void testScheduleSnapshot_AcceptedWhenOtherSnapshotIsScheduled() {
        StubExecutor executor = new StubExecutor();
        testSubject = TestSnapshotter.builder().eventStore(mockEventStore).executor(executor).build();

        testSubject.scheduleSnapshot(Object.class, "id1");
        testSubject.scheduleSnapshot(Object.class, "id2");

        assertEquals(2, executor.size());
        executor.executeNext();
        assertEquals(1, executor.size());

        testSubject.scheduleSnapshot(Object.class, "id2");

        assertEquals(1, executor.size());
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

            @Override
            public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
                super.spanFactory(spanFactory);
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

    private class StubExecutor implements Executor {

        private final Queue<Runnable> tasks = new LinkedList<>();

        @Override
        public void execute(@Nonnull Runnable runnable) {
            tasks.add(runnable);
        }

        public boolean executeNext() {
            Runnable next = tasks.poll();
            if (next != null) {
                next.run();
                return true;
            }
            return false;
        }

        public int size() {
            return tasks.size();
        }
    }
}
