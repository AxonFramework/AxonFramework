package org.axonframework.messaging.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.Before;
import org.junit.Test;

public class SimpleResourceParameterResolverFactoryTest {

    private static final String TEST_RESOURCE = "testResource";

    private SimpleResourceParameterResolverFactory testSubject;

    private Method messageHandlingMethodWithResourceParameter;
    private Method messageHandlingMethodWithoutResourceParameter;
    private Method messageHandlingMethodWithResourceParameterOfDifferentType;

    @Before
    public void setUp() throws Exception {
        testSubject = new SimpleResourceParameterResolverFactory(TEST_RESOURCE);

        messageHandlingMethodWithResourceParameter = getClass().getMethod("someMessageHandlingMethodWithResource", Message.class, String.class);
        messageHandlingMethodWithoutResourceParameter = getClass().getMethod("someMessageHandlingMethodWithoutResource", Message.class);
        messageHandlingMethodWithResourceParameterOfDifferentType =
                getClass().getMethod("someMessageHandlingMethodWithResourceOfDifferentType", Message.class, Integer.class);
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithResource(Message message, String resource) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithoutResource(Message message) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageHandlingMethodWithResourceOfDifferentType(Message message, Integer resourceOfDifferentType) {
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResolvesToResourceWhenMessageHandlingMethodHasResourceParameter() throws Exception {
        ParameterResolver resolver =
                testSubject.createInstance(messageHandlingMethodWithResourceParameter, messageHandlingMethodWithResourceParameter.getParameters(), 1);
        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(eventMessage));
        assertEquals(TEST_RESOURCE, resolver.resolveParameterValue(eventMessage));
    }

    @Test
    public void testIgnoredWhenMessageHandlingMethodHasNoResourceParameter() throws Exception {
        ParameterResolver resolver =
                testSubject.createInstance(messageHandlingMethodWithoutResourceParameter, messageHandlingMethodWithoutResourceParameter.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    public void testIgnoredWhenMessageHandlingMethodHasResourceParameterOfDifferentType() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(messageHandlingMethodWithResourceParameterOfDifferentType, messageHandlingMethodWithResourceParameterOfDifferentType.getParameters(), 1);
        assertNull(resolver);
    }

}