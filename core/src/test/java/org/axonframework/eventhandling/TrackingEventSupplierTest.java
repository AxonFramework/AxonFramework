/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
public class TrackingEventSupplierTest {

    private TrackingEventSupplier testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventProcessor eventProcessor;
    private EventListener mockListener;

    @Before
    public void setUp() throws Exception {
        eventBus = new EmbeddedEventStore(new InMemoryEventStorageEngine());
        tokenStore = spy(new InMemoryTokenStore());
        mockListener = mock(EventListener.class);
        eventProcessor = new PublishingEventProcessor("test", mockListener);
        testSubject = new TrackingEventSupplier(eventBus, tokenStore, eventProcessor, 0);

        eventBus.initialize();
        testSubject.initialize();
    }

    @After
    public void tearDown() throws Exception {
        eventBus.shutDown();
        testSubject.shutDown();
    }

    @Test
    public void testPublishedEventsGetPassedToListener() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());
        eventBus.publish(createEvents(2));
        assertTrue(countDownLatch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testTokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        eventProcessor.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        assertTrue(countDownLatch.await(100, TimeUnit.MILLISECONDS));
        verify(tokenStore).storeToken(any(), anyInt(), any());
        assertNotNull(tokenStore.fetchToken(eventProcessor.getName(), 0));
    }

    @Test
    public void testTokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        eventProcessor.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                throw new MockException();
            });
            return interceptorChain.proceed();
        }));
        eventProcessor.registerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        assertTrue(countDownLatch.await(100, TimeUnit.MILLISECONDS));
        verify(tokenStore).storeToken(any(), anyInt(), any());
        assertNull(tokenStore.fetchToken(eventProcessor.getName(), 0));
    }

    @Test
    @DirtiesContext
    public void testContinueFromPreviousToken() throws Exception {
        testSubject.shutDown();

        eventBus.publish(createEvents(10));
        TrackedEventMessage<?> firstEvent = eventBus.streamEvents(null).nextAvailable();
        tokenStore.storeToken(eventProcessor.getName(), 0, firstEvent.trackingToken());
        assertEquals(firstEvent.trackingToken(), tokenStore.fetchToken(eventProcessor.getName(), 0));

        List<EventMessage<?>> ackedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            ackedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockListener).handle(any());

        testSubject = new TrackingEventSupplier(eventBus, tokenStore, eventProcessor, 0);
        testSubject.initialize();
        assertTrue(countDownLatch.await(100, TimeUnit.MILLISECONDS));
        assertEquals(9, ackedEvents.size());
    }

}