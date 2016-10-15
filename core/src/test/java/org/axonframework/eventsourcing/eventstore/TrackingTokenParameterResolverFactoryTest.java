package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class TrackingTokenParameterResolverFactoryTest {

    private Method method;
    private TrackingTokenParameterResolverFactory testSubject;

    @Before
    public void setUp() throws Exception {
        method = getClass().getDeclaredMethod("method1", Object.class, TrackingToken.class);
        testSubject = new TrackingTokenParameterResolverFactory();
    }

    @Test
    public void createInstance() throws Exception {
        assertNull(testSubject.createInstance(method, method.getParameters(), 0));
        ParameterResolver<?> resolver = testSubject.createInstance(method, method.getParameters(), 1);

        assertNotNull(resolver);
        GenericEventMessage<String> message = new GenericEventMessage<>("test");
        assertFalse(resolver.matches(message));
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(1L);
        GenericTrackedEventMessage<String> trackedEventMessage = new GenericTrackedEventMessage<>(trackingToken,
                                                                                       message);
        assertTrue(resolver.matches(trackedEventMessage));
        assertSame(trackingToken, resolver.resolveParameterValue(trackedEventMessage));
    }

    @SuppressWarnings("unused")
    private void method1(Object param1, TrackingToken token) {

    }

}
