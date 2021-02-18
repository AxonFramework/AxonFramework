package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotatedMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class AnnotatedMessageHandlingMemberTest {

    private AnnotatedMessageHandlingMember<AnnotatedHandler> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotatedMessageHandlingMember<>(
                AnnotatedHandler.class.getMethods()[0],
                EventMessage.class,
                String.class,
                ClasspathParameterResolverFactory.forClass(AnnotatedHandler.class)
        );
    }

    @Test
    void testCanHandleMessageType() {
        assertTrue(testSubject.canHandleMessageType(EventMessage.class));
        assertFalse(testSubject.canHandleMessageType(CommandMessage.class));
    }

    @Test
    void testIsA() {
        assertTrue(testSubject.isA(EventHandler.class.getSimpleName()));
        assertTrue(testSubject.isA(MessageHandler.class.getSimpleName()));
        assertFalse(testSubject.isA(CommandHandler.class.getSimpleName()));
    }

    @Test
    void testAttributesReturnsEmptyOptionalForNonMatchingHandlerType() {
        Optional<Map<String, Object>> result = testSubject.attributes(CommandHandler.class.getSimpleName());

        assertFalse(result.isPresent());
    }

    @Test
    void testAttributesReturnsNonEmptyOptionalForMatchingHandlerType() {
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", EventMessage.class);
        expectedMessageHandlerAttributes.put("payloadType", Object.class);

        Optional<Map<String, Object>> result = testSubject.attributes(MessageHandler.class.getSimpleName());

        assertTrue(result.isPresent());
        assertEquals(expectedMessageHandlerAttributes, result.get());
    }

    @Test
    void testAttributeReturnsNonEmptyOptionalForMatchingAttributeKey() {
        Optional<Object> resultMessageType = testSubject.attribute(HandlerAttributeDictionary.MESSAGE_TYPE);
        Optional<Object> resultPayloadType = testSubject.attribute(HandlerAttributeDictionary.PAYLOAD_TYPE);

        assertTrue(resultMessageType.isPresent());
        assertEquals(EventMessage.class, resultMessageType.get());

        assertTrue(resultPayloadType.isPresent());
        assertEquals(Object.class, resultPayloadType.get());
    }

    @SuppressWarnings("unused")
    private static class AnnotatedHandler {

        @EventHandler
        public void handlingMethod(String event) {

        }
    }
}