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

package org.axonframework.spring.eventhandling.annotation;

import net.sf.cglib.proxy.Enhancer;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.spring.eventhandling.StubDomainEvent;
import org.junit.*;
import org.mockito.*;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationEventListenerBeanPostProcessorTest {

    private AnnotationEventListenerBeanPostProcessor testSubject;
    private EventBus mockEventBus;
    private ApplicationContext mockApplicationContext;
    private Cluster mockCluster;

    @Before
    public void setUp() {
        mockEventBus = mock(EventBus.class);
        mockApplicationContext = mock(ApplicationContext.class);
        testSubject = Mockito.spy(new AnnotationEventListenerBeanPostProcessor());
        testSubject.setEventBus(mockEventBus);
        testSubject.setApplicationContext(mockApplicationContext);

        mockCluster = mock(Cluster.class);
        when(mockApplicationContext.getBeansOfType(Cluster.class)).thenReturn(singletonMap("defaultCluster",
                                                                                           mockCluster));
    }

    @Test
    public void testEventBusIsNotAutowiredWhenProvided() throws Exception {

        testSubject.initializeAdapterFor(new Object(), mock(ParameterResolverFactory.class));

        Mockito.verify(mockApplicationContext, never()).getBeansOfType(EventBus.class);
    }

    @Test
    public void testBeanSubscribedToAutowiredEventBus() throws Exception {
        testSubject.setEventBus(null);
        Map<String, EventBus> map = new HashMap<>();
        map.put("ignored", mockEventBus);
        when(mockApplicationContext.getBeansOfType(EventBus.class)).thenReturn(map);

        final AnnotationEventListenerAdapter adapter = mock(AnnotationEventListenerAdapter.class);
        EventListener mockProxy = mock(EventListener.class);
        testSubject.subscribe(mockProxy, adapter);

        Mockito.verify(mockApplicationContext).getBeansOfType(EventBus.class);
        Mockito.verify(mockEventBus).subscribe(mockCluster);
        Mockito.verify(mockCluster).subscribe(mockProxy);
        Mockito.verifyZeroInteractions(adapter);
    }

    @Test
    public void testEventHandlerCallsRedirectToAdapter() {
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
    public void testEventHandlerCallsRedirectToAdapter_ExceptionPropagated() {
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
    public void testEventHandlerAdapterIsInitializedAndDestroyedProperly_NoStopSignal() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        EventListener postProcessedBean = (EventListener) testSubject.postProcessAfterInitialization(result1,
                                                                                                     "beanName");

        Mockito.verify(mockEventBus, never()).subscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockEventBus, never()).unsubscribe(Matchers.isA(Cluster.class));

        testSubject.start();

        Mockito.verify(mockEventBus).subscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockEventBus, never()).unsubscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockCluster, never()).unsubscribe(Matchers.isA(EventListener.class));

        testSubject.postProcessBeforeDestruction(postProcessedBean, "beanName");

        Mockito.verify(mockCluster).unsubscribe(Matchers.isA(EventListener.class));
    }

    @Test
    public void testEventHandlerAdapterIsInitializedAndDestroyedProperly_NormalLifecycle() throws Exception {
        Object result1 = testSubject.postProcessBeforeInitialization(new SyncEventListener(), "beanName");
        EventListener postProcessedBean = (EventListener) testSubject.postProcessAfterInitialization(result1,
                                                                                                     "beanName");

        Mockito.verify(mockEventBus, never()).subscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockEventBus, never()).unsubscribe(Matchers.isA(Cluster.class));

        testSubject.start();

        Mockito.verify(mockEventBus).subscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockEventBus, never()).unsubscribe(Matchers.isA(Cluster.class));
        Mockito.verify(mockCluster, never()).unsubscribe(postProcessedBean);

        testSubject.stop();

        Mockito.verify(mockCluster).unsubscribe(postProcessedBean);
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
