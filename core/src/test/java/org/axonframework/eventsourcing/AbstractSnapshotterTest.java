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

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.TransactionManager;
import org.axonframework.repository.ConcurrencyException;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Allard Buijze
 */
public class AbstractSnapshotterTest {

    private AbstractSnapshotter testSubject;
    private SnapshotEventStore mockEventStore;
    private Logger logger;
    private Logger originalLogger;

    @Before
    public void setUp() throws Exception {
        mockEventStore = mock(SnapshotEventStore.class);
        testSubject = new AbstractSnapshotter() {
            @Override
            protected DomainEventMessage createSnapshot(Class<? extends EventSourcedAggregateRoot> aggregateType,
                                                        String aggregateIdentifier, DomainEventStream eventStream) {
                long lastIdentifier = getLastIdentifierFrom(eventStream);
                if (lastIdentifier <= 0) {
                    return null;
                }
                return new GenericDomainEventMessage<>(aggregateIdentifier, lastIdentifier,
                                                       "Mock contents", MetaData.emptyInstance());
            }
        };
        testSubject.setEventStore(mockEventStore);
        testSubject.setExecutor(DirectExecutor.INSTANCE);
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
                .thenReturn(new SimpleDomainEventStream(
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                        "Mock contents", MetaData.emptyInstance()),
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                        "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot(EventSourcedAggregateRoot.class, aggregateIdentifier);
        verify(mockEventStore).appendSnapshotEvent(argThat(event(aggregateIdentifier, 1)));
    }

    @Test
    public void testScheduleSnapshot_ConcurrencyExceptionIsSilenced()
            throws NoSuchFieldException, IllegalAccessException {
        final String aggregateIdentifier = "aggregateIdentifier";
        doNothing()
                .doThrow(new ConcurrencyException("Mock"))
                .when(mockEventStore).appendSnapshotEvent(isA(DomainEventMessage.class));
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenAnswer(invocationOnMock -> new SimpleDomainEventStream(
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                        "Mock contents", MetaData.emptyInstance()),
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 1,
                                                        "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot(EventSourcedAggregateRoot.class, aggregateIdentifier);

        testSubject.scheduleSnapshot(EventSourcedAggregateRoot.class, aggregateIdentifier);
        verify(mockEventStore, times(2)).appendSnapshotEvent(argThat(event(aggregateIdentifier, 1)));
        verify(logger, never()).warn(anyString());
        verify(logger, never()).error(anyString());
    }

    @Test
    public void testScheduleSnapshot_SnapshotIsNull() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 0,
                                                        "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot(EventSourcedAggregateRoot.class, aggregateIdentifier);
        verify(mockEventStore, never()).appendSnapshotEvent(any(DomainEventMessage.class));
    }

    @Test
    public void testScheduleSnapshot_SnapshotReplacesOneEvent() {
        String aggregateIdentifier = "aggregateIdentifier";
        when(mockEventStore.readEvents(aggregateIdentifier))
                .thenReturn(new SimpleDomainEventStream(
                        new GenericDomainEventMessage<>(aggregateIdentifier, (long) 2,
                                                        "Mock contents", MetaData.emptyInstance())));
        testSubject.scheduleSnapshot(EventSourcedAggregateRoot.class, aggregateIdentifier);
        verify(mockEventStore, never()).appendSnapshotEvent(any(DomainEventMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testScheduleSnapshot_WithTransaction() {
        final TransactionManager txManager = mock(TransactionManager.class);
        testSubject.setTxManager(txManager);

        testScheduleSnapshot();

        InOrder inOrder = inOrder(mockEventStore, txManager);
        inOrder.verify(txManager).startTransaction();
        inOrder.verify(mockEventStore).readEvents(anyString());
        inOrder.verify(mockEventStore).appendSnapshotEvent(isA(DomainEventMessage.class));
        inOrder.verify(txManager).commitTransaction(anyObject());
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
}
