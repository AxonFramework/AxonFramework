package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SourceIdParameterResolverFactoryTest {

    private SourceIdParameterResolverFactory testSubject;

    private Method sourceIdMethod;
    private Method nonAnnotatedMethod;
    private Method integerMethod;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new SourceIdParameterResolverFactory();

        sourceIdMethod = getClass().getMethod("someSourceIdMethod", String.class);
        nonAnnotatedMethod = getClass().getMethod("someNonAnnotatedMethod", String.class);
        integerMethod = getClass().getMethod("someIntegerMethod", Integer.class);
    }

    @SuppressWarnings("unused")
    public void someSourceIdMethod(@SourceId String id) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someNonAnnotatedMethod(String id) {
        //Used in setUp()
    }

    @SuppressWarnings("unused")
    public void someIntegerMethod(@SourceId Integer id) {
        //Used in setUp()
    }

    @Test
    void testResolvesToAggregateIdentifierWhenAnnotatedForDomainEventMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(sourceIdMethod, sourceIdMethod.getParameters(), 0);
        final GenericDomainEventMessage<Object> eventMessage =
                new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0L, null);
        assertTrue(resolver.matches(eventMessage));
        assertEquals(eventMessage.getAggregateIdentifier(), resolver.resolveParameterValue(eventMessage));
    }

    @Test
    void testDoesNotMatchWhenAnnotatedForCommandMessage() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(sourceIdMethod, sourceIdMethod.getParameters(), 0);
        CommandMessage<Object> commandMessage = GenericCommandMessage.asCommandMessage("test");
        assertFalse(resolver.matches(commandMessage));
    }

    @Test
    void testIgnoredWhenNotAnnotated() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(nonAnnotatedMethod, nonAnnotatedMethod.getParameters(), 0);
        assertNull(resolver);
    }

    @Test
    void testIgnoredWhenWrongType() {
        ParameterResolver<String> resolver =
                testSubject.createInstance(integerMethod, integerMethod.getParameters(), 0);
        assertNull(resolver);
    }
}