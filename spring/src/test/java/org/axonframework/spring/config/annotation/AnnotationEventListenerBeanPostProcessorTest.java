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

package org.axonframework.spring.config.annotation;

import net.sf.cglib.proxy.Enhancer;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotationEventListenerBeanPostProcessorTest {

    private AnnotationEventListenerBeanPostProcessor testSubject;

    @Before
    public void setUp() {
        testSubject = Mockito.spy(new AnnotationEventListenerBeanPostProcessor());
    }

    @Test
    public void testEventHandlerCallsRedirectToAdapter() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof EventListener);
        assertTrue(postProcessedBean instanceof SyncEventListener);

        EventListener eventListener = (EventListener) postProcessedBean;
        SyncEventListener annotatedEventListener = (SyncEventListener) postProcessedBean;
        StubDomainEvent domainEvent = new StubDomainEvent();
        eventListener.handle(new GenericEventMessage<>(domainEvent));

        assertEquals(1, annotatedEventListener.getInvocationCount());
    }

    @Test
    public void testPostProcessedBeanNotProcessedAgain() {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");
        Object doubleProcessedBean = testSubject.postProcessBeforeInitialization(postProcessedBean, "beanName");
        doubleProcessedBean = testSubject.postProcessAfterInitialization(doubleProcessedBean, "beanName");

        assertTrue(postProcessedBean instanceof EventListener);
        assertTrue(postProcessedBean instanceof SyncEventListener);
        assertSame("Bean should not have been processed again", doubleProcessedBean, postProcessedBean);
    }

    @Test
    public void testAggregatesAreNotEligibleForPostProcessing() {
        StubAggregate aggregate = new StubAggregate();
        Object actualResult = testSubject.postProcessAfterInitialization(aggregate, "aggregate");
        Assert.assertEquals(aggregate.getClass(), actualResult.getClass());
        assertSame("Actual result was modified. It has probably been proxied", aggregate, actualResult);
    }

    @Test
    // verifies issue #73
    public void testEventHandlerCallsRedirectToAdapter_ExceptionPropagated() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        Object postProcessedBean = testSubject.postProcessAfterInitialization(result1, "beanName");

        assertTrue(postProcessedBean instanceof EventListener);
        assertTrue(postProcessedBean instanceof SyncEventListener);

        EventListener eventListener = (EventListener) postProcessedBean;
        SyncEventListener annotatedEventListener = (SyncEventListener) postProcessedBean;
        FailingEvent domainEvent = new FailingEvent();
        try {
            eventListener.handle(new GenericEventMessage<>(domainEvent));
            fail("Expected exception to be propagated");
        } catch (RuntimeException e) {
            assertEquals("Don't like this event", e.getMessage());
        }
    }

    @Test
    public void testPostProcessBean_AlreadyHandlerIsNotEnhanced() {
        RealEventListener eventHandler = new RealEventListener();
        Object actualResult = testSubject.postProcessAfterInitialization(eventHandler, "beanName");
        assertFalse(Enhancer.isEnhanced(actualResult.getClass()));
        assertSame(eventHandler, actualResult);
    }

    @Test
    public void testPostProcessBean_PlainObjectIsIgnored() {
        NotAnEventHandler eventHandler = new NotAnEventHandler();
        Object actualResult = testSubject.postProcessAfterInitialization(eventHandler, "beanName");
        assertFalse(Enhancer.isEnhanced(actualResult.getClass()));
        assertSame(eventHandler, actualResult);
    }

    public static class NotAnEventHandler {

    }

    public static class RealEventListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
            // not relevant
        }

        @EventHandler
        public void handleEvent(Object event) {

        }
    }

    public static class SyncEventListener {

        private int invocationCount;

        @EventHandler
        public void handleEvent(Object event) {
            invocationCount++;
        }

        @EventHandler
        public void handleFailingEvent(FailingEvent event) {
            throw new RuntimeException("Don't like this event");
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }

    public static class FailingEvent {

    }
}
