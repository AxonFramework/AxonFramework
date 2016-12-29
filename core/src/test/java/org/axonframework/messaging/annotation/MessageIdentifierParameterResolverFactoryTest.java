package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class MessageIdentifierParameterResolverFactoryTest {

    private MessageIdentifierParameterResolverFactory testSubject;

    private Method messageIdentifierMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @Before
    public void setUp() throws Exception {
        testSubject = new MessageIdentifierParameterResolverFactory();

        messageIdentifierMethod = getClass().getMethod("someMessageIdentifierMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someMessageIdentifierMethod(@MessageIdentifier String messageIdentifier) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someNonAnnotatedMethod(String messageIdentifier) {
    }

    @SuppressWarnings("unused") //Used in setUp()
    public void someIntegerMethod(@MessageIdentifier Integer messageIdentifier) {
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResolvesToMessageIdentifierWhenAnnotatedForEventMessage() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(messageIdentifierMethod, messageIdentifierMethod.getParameters(), 0);
        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        assertTrue(resolver.matches(eventMessage));
        assertEquals(eventMessage.getIdentifier(), resolver.resolveParameterValue(eventMessage));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResolvesToMessageIdentifierWhenAnnotatedForCommandMessage() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(messageIdentifierMethod, messageIdentifierMethod.getParameters(), 0);
        CommandMessage<Object> commandMessage = GenericCommandMessage.asCommandMessage("test");
        assertTrue(resolver.matches(commandMessage));
        assertEquals(commandMessage.getIdentifier(), resolver.resolveParameterValue(commandMessage));
    }

    @Test
    public void testIgnoredWhenNotAnnotated() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(nonAnnotatedMethod, nonAnnotatedMethod.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    public void testIgnoredWhenWrongType() throws Exception {
        ParameterResolver resolver = testSubject.createInstance(integerMethod, integerMethod.getParameters(), 0);
        assertNull(resolver);
    }

}