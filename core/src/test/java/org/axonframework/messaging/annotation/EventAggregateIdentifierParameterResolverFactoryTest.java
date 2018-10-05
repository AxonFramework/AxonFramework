package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.Assert.*;

public class EventAggregateIdentifierParameterResolverFactoryTest {
    private EventAggregateIdentifierParameterResolverFactory testSubject;

    private Method aggregateIdentifierMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @Before
    public void setUp() throws Exception {
        testSubject = new EventAggregateIdentifierParameterResolverFactory();

        aggregateIdentifierMethod = getClass().getMethod("someEventAggregateIdentifierMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings("unused")
    public void someEventAggregateIdentifierMethod(@EventAggregateIdentifier String messageIdentifier) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someNonAnnotatedMethod(String messageIdentifier) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someIntegerMethod(@EventAggregateIdentifier Integer messageIdentifier) {
        //Used in setUp()
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResolvesToAggregateIdentifierWhenAnnotatedForDomainEventMessage() {
        ParameterResolver resolver = testSubject.createInstance(aggregateIdentifierMethod, aggregateIdentifierMethod.getParameters(), 0);
        final GenericDomainEventMessage<Object> eventMessage = new GenericDomainEventMessage("test", UUID.randomUUID().toString(), 0L, null);
        assertTrue(resolver.matches(eventMessage));
        assertEquals(eventMessage.getAggregateIdentifier(), resolver.resolveParameterValue(eventMessage));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoesNotMatchWhenAnnotatedForCommandMessage() {
        ParameterResolver resolver = testSubject.createInstance(aggregateIdentifierMethod, aggregateIdentifierMethod.getParameters(), 0);
        CommandMessage<Object> commandMessage = GenericCommandMessage.asCommandMessage("test");
        assertFalse(resolver.matches(commandMessage));
    }

    @Test
    public void testIgnoredWhenNotAnnotated() {
        ParameterResolver resolver = testSubject.createInstance(nonAnnotatedMethod, nonAnnotatedMethod.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    public void testIgnoredWhenWrongType() {
        ParameterResolver resolver = testSubject.createInstance(integerMethod, integerMethod.getParameters(), 0);
        assertNull(resolver);
    }
}