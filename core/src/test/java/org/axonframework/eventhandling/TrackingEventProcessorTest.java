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

package org.axonframework.eventhandling;

import org.axonframework.common.MockException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.*;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class TrackingEventProcessorTest {

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventListener mockListener;

    @Before
    public void setUp() throws Exception {
        tokenStore = spy(new InMemoryTokenStore());
        mockListener = mock(EventListener.class);
        eventHandlerInvoker = new SimpleEventHandlerInvoker(mockListener);
        eventBus = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore, NoTransactionManager.INSTANCE);

        testSubject.start();
    }

    @After
    public void tearDown() throws Exception {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    public void testPublishedEventsGetPassedToListener() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());
        eventBus.publish(createEvents(2));
        assertTrue("Expected listener to have received 2 published events", countDownLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testTokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    public void testTokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                throw new MockException();
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    public void testContinueFromPreviousToken() throws Exception {
        testSubject.shutDown();

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));

        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore, NoTransactionManager.INSTANCE);
        testSubject.start();
        assertTrue("Expected 9 invocations on event listener by now", countDownLatch.await(5, TimeUnit.SECONDS));
        assertEquals(9, ackedEvents.size());
    }

    @Test
    public void testFirstTokenIsStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));
        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(events);
        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNotNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    @SuppressWarnings("unchecked")
    public void testEventsWithTheSameTokenAreProcessedInTheSameBatch() throws Exception {
        testSubject.shutDown();
        eventBus.shutDown();

        eventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());
        when(eventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator()));
        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore, NoTransactionManager.INSTANCE);

        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));

        CountDownLatch countDownLatch = new CountDownLatch(2);

        testSubject.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));

        testSubject.start();

        assertTrue("Expected Unit of Work to have reached clean up phase", countDownLatch.await(5, TimeUnit.SECONDS));
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    private static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator) {
        return new TrackingEventStream() {
            private boolean hasPeeked;
            private TrackedEventMessage<?> peekEvent;

            @Override
            public Optional<TrackedEventMessage<?>> peek() {
                if (!hasPeeked) {
                    if (!hasNextAvailable()) {
                        return Optional.empty();
                    }
                    peekEvent = iterator.next();
                    hasPeeked = true;
                }
                return Optional.of(peekEvent);
            }

            @Override
            public boolean hasNextAvailable(int timeout, TimeUnit unit) {
                return hasPeeked || iterator.hasNext();
            }

            @Override
            public TrackedEventMessage nextAvailable() {
                if (!hasPeeked) {
                    return iterator.next();
                }
                TrackedEventMessage<?> result = peekEvent;
                peekEvent = null;
                hasPeeked = false;
                return result;
            }

            @Override
            public void close() {

            }
        };
    }

}
