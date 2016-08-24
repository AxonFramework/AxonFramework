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
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.*;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.mockito.Matchers.any;
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
        eventBus = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        tokenStore = spy(new InMemoryTokenStore());
        mockListener = mock(EventListener.class);
        eventHandlerInvoker = new SimpleEventHandlerInvoker(mockListener);
        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore);

        testSubject.start();
    }

    @After
    public void tearDown() throws Exception {
        testSubject.shutdown();
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
        verify(tokenStore).storeToken(any(), any(), anyInt());
        assertNull(tokenStore.fetchToken(testSubject.getName(), 0));
    }

    @Test
    @DirtiesContext
    public void testContinueFromPreviousToken() throws Exception {
        testSubject.shutdown();

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.streamEvents(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(testSubject.getName(), 0));

        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject = new TrackingEventProcessor("test", eventHandlerInvoker, eventBus, tokenStore);
        testSubject.start();
        assertTrue("Expected 9 invocations on event listener by now", countDownLatch.await(5, TimeUnit.SECONDS));
        assertEquals(9, ackedEvents.size());
    }

}
