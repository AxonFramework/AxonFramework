/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.utils.RecordingAppender;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.utils.EventStoreTestUtils.createEvents;
import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AbstractSnapshotter}.
 */
class AbstractSnapshotterTest {

    private EventStore mockEventStore;
    private TestSpanFactory spanFactory;

    private AbstractSnapshotter testSubject;

    @BeforeEach
    void setUp() throws Exception {
        mockEventStore = mock(EventStore.class);
        spanFactory = new TestSpanFactory();
        SnapshotterSpanFactory snapshotterSpanFactory = DefaultSnapshotterSpanFactory.builder()
                                                                                     .spanFactory(spanFactory)
                                                                                     .build();
        testSubject = TestSnapshotter.builder()
                                     .eventStore(mockEventStore)
                                     .spanFactory(snapshotterSpanFactory)
                                     .build();
    }

    @AfterEach
    void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void scheduleSnapshot() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    void snapshotTracing() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanActive("Snapshotter.scheduleSnapshot(Object)");
                    spanFactory.verifySpanActive("Snapshotter.createSnapshot(Object)");
                    return DomainEventStream.of(createEvents(2));
                });
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
        spanFactory.verifySpanCompleted("Snapshotter.scheduleSnapshot(Object)");
        spanFactory.verifySpanCompleted("Snapshotter.createSnapshot(Object)");
        spanFactory.verifySpanHasType("Snapshotter.scheduleSnapshot(Object)", TestSpanFactory.TestSpanType.INTERNAL);
        spanFactory.verifySpanHasType("Snapshotter.createSnapshot(Object)",
                                      TestSpanFactory.TestSpanType.INTERNAL);
    }

    @Test
    void scheduleSnapshotIsPostponedUntilUnitOfWorkAfterCommit() {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(argThat(event(aggregateIdentifier, 1)));

        uow.commit();

        verify(mockEventStore).storeSnapshot(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    void scheduleSnapshotOnlyOnce() {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvents(2)));
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
    void scheduleSnapshot_ConcurrencyExceptionIsSilenced() {
        RecordingAppender recordingAppender = RecordingAppender.getInstance();
        recordingAppender.startRecording(e -> e.getLevel().isMoreSpecificThan(Level.WARN));

        final String aggregateIdentifier = "aggregateIdentifier";
        doNothing().doThrow(new ConcurrencyException("Mock"))
                   .when(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenAnswer(invocationOnMock -> DomainEventStream.of(createEvents(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);

        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, times(2)).storeSnapshot(argThat(event(aggregateIdentifier, 1)));

        List<LogEvent> logEvents = recordingAppender.stopRecording();
        assertEquals(Collections.emptyList(), logEvents);
    }

    @Test
    void scheduleSnapshot_SnapshotIsNull() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent()));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @Test
    void scheduleSnapshot_SnapshotReplacesOneEvent() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier)).thenReturn(DomainEventStream.of(createEvent(2)));
        testSubject.scheduleSnapshot(Object.class, aggregateIdentifier);
        verify(mockEventStore, never()).storeSnapshot(any(DomainEventMessage.class));
    }

    @Test
    void scheduleSnapshot_WithTransaction() {
        Transaction mockTransaction = mock(Transaction.class);
        TransactionManager txManager = spy(new StubTransactionManager(mockTransaction));
        when(txManager.startTransaction()).thenReturn(mockTransaction);

        testSubject = TestSnapshotter.builder()
                                     .eventStore(mockEventStore)
                                     .transactionManager(txManager)
                                     .build();

        scheduleSnapshot();

        InOrder inOrder = inOrder(mockEventStore, txManager, mockTransaction);
        inOrder.verify(txManager).startTransaction();
        inOrder.verify(mockEventStore).readEvents(anyString());
        inOrder.verify(mockEventStore).storeSnapshot(isA(DomainEventMessage.class));
        inOrder.verify(mockTransaction).commit();
    }

    @Test
    void scheduleSnapshot_IgnoredWhenSnapshotAlreadyScheduled() {
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
    void scheduleSnapshot_AcceptedWhenOtherSnapshotIsScheduled() {
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

    @SuppressWarnings("SameParameterValue")
    private ArgumentMatcher<DomainEventMessage<?>> event(final Object aggregateIdentifier, final long i) {
        return x -> aggregateIdentifier.equals(x.getAggregateIdentifier()) && x.getSequenceNumber() == i;
    }

    private static class TestSnapshotter extends AbstractSnapshotter {

        private TestSnapshotter(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        protected DomainEventMessage<?> createSnapshot(Class<?> aggregateType,
                                                       String aggregateIdentifier,
                                                       DomainEventStream eventStream) {
            long lastIdentifier = getLastIdentifierFrom(eventStream);
            if (lastIdentifier <= 0) {
                return null;
            }
            return new GenericDomainEventMessage<>("test", aggregateIdentifier, lastIdentifier,
                                                   QualifiedNameUtils.fromDottedName("test.event"), "Mock contents");
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
            public Builder spanFactory(@Nonnull SnapshotterSpanFactory spanFactory) {
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

    private static class StubExecutor implements Executor {

        private final Queue<Runnable> tasks = new LinkedList<>();

        @Override
        public void execute(@Nonnull Runnable runnable) {
            tasks.add(runnable);
        }

        @SuppressWarnings("UnusedReturnValue")
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
