/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.DomainEvent;
import org.axonframework.core.Event;
import org.axonframework.core.StubDomainEvent;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventSequencingPolicy;
import org.axonframework.core.eventhandler.FullConcurrencyPolicy;
import org.axonframework.core.eventhandler.SequentialPolicy;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationEventListenerAdapterTest {

    @Test
    public void testHandlerCorrectlyUsed() {
        ConcurrentAnnotatedEventHandler annotatedEventHandler = new ConcurrentAnnotatedEventHandler();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventHandler);

        adapter.beforeTransaction(null);
        assertEquals(1, annotatedEventHandler.beforeInvoked);
        assertEquals(0, annotatedEventHandler.eventInvoked);
        assertEquals(0, annotatedEventHandler.afterInvoked);
        adapter.handle(new StubDomainEvent());
        assertEquals(1, annotatedEventHandler.beforeInvoked);
        assertEquals(1, annotatedEventHandler.eventInvoked);
        assertEquals(0, annotatedEventHandler.afterInvoked);
        adapter.afterTransaction(null);
        assertEquals(1, annotatedEventHandler.beforeInvoked);
        assertEquals(1, annotatedEventHandler.eventInvoked);
        assertEquals(1, annotatedEventHandler.afterInvoked);

        assertTrue(adapter.canHandle(StubDomainEvent.class));
        assertFalse(adapter.canHandle(DomainEvent.class));
        assertNotNull(adapter.getConfigurationFor(new StubDomainEvent()));
    }

    @Test
    public void testAdapterMangegesEventBusSubscription() {
        ConcurrentAnnotatedEventHandler annotatedEventHandler = new ConcurrentAnnotatedEventHandler();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventHandler);
        EventBus mockEventBus = mock(EventBus.class);
        adapter.setEventBus(mockEventBus);

        adapter.initialize();
        verify(mockEventBus).subscribe(adapter);
        adapter.shutdown();
        verify(mockEventBus).unsubscribe(adapter);
    }

    @Test
    public void testHandlingPolicy_Default() throws Exception {
        AnnotatedEventHandler annotatedEventHandler = new AnnotatedEventHandler();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventHandler);

        EventSequencingPolicy actualPolicy = adapter.getEventSequencingPolicy();

        assertEquals(SequentialPolicy.class, actualPolicy.getClass());
    }

    @Test
    public void testHandlingPolicy_FromAnnotation() throws Exception {
        ConcurrentAnnotatedEventHandler annotatedEventHandler = new ConcurrentAnnotatedEventHandler();
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(annotatedEventHandler);

        EventSequencingPolicy actualPolicy = adapter.getEventSequencingPolicy();

        assertEquals(FullConcurrencyPolicy.class, actualPolicy.getClass());
    }

    @Test
    public void testHandlingPolicy_IllegalClassFromAnnotation() throws Exception {
        IllegalConcurrentAnnotatedEventHandler annotatedEventHandler = new IllegalConcurrentAnnotatedEventHandler();
        try {
            new AnnotationEventListenerAdapter(annotatedEventHandler);
            fail("Expected UnsupportedPolicyException");
        }
        catch (UnsupportedPolicyException e) {
            assertTrue("Incomplete message:" + e.getMessage(), e.getMessage().contains("IllegalConcurrencyPolicy"));
        }
    }

    private static class AnnotatedEventHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }

    }

    @ConcurrentEventListener(sequencingPolicyClass = FullConcurrencyPolicy.class)
    private static class ConcurrentAnnotatedEventHandler {

        private int beforeInvoked;
        private int afterInvoked;
        private int eventInvoked;

        @BeforeTransaction
        public void beforeTransaction() {
            beforeInvoked++;
        }

        @EventHandler
        public void handleEvent(StubDomainEvent event) {
            eventInvoked++;
        }

        @AfterTransaction
        public void afterTransaction() {
            afterInvoked++;
        }

    }

    @ConcurrentEventListener(sequencingPolicyClass = IllegalConcurrencyPolicy.class)
    private static class IllegalConcurrentAnnotatedEventHandler {

        @EventHandler
        public void handleEvent(Event event) {
        }

    }

    private static class IllegalConcurrencyPolicy implements EventSequencingPolicy {

        public IllegalConcurrencyPolicy(String name) {
            // just for the sake of not having a default constructor
        }

        @Override
        public Object getSequenceIdentifierFor(Event event) {
            return null;
        }
    }
}
