package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.AllowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.ResultHandler;
import org.junit.jupiter.api.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating several approaches of annotated handlers, for which the {@link HandlerAttributes} are provided
 * by the {@link HandlerAttributesUtils#handlerAttributes(AnnotatedElement)}.
 *
 * @author Steven van Beelen
 */
class HandlerAttributesUtilsTest {

    @Test
    void testHandlerAttributesOnAnnotatedCommandHandler() throws NoSuchMethodException {
        Method messageHandlingMember = AnnotatedCommandHandler.class.getMethod("handle", Object.class);

        HandlerAttributes expected = new HandlerAttributes();
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", CommandMessage.class);
        expectedMessageHandlerAttributes.put("payloadType", String.class);
        expected.put(MessageHandler.class.getSimpleName(), expectedMessageHandlerAttributes);
        Map<String, Object> expectedCommandHandlerAttributes = new HashMap<>();
        expectedCommandHandlerAttributes.put("commandName", "my-command");
        expectedCommandHandlerAttributes.put("routingKey", "my-routing-key");
        expectedCommandHandlerAttributes.put("payloadType", String.class);
        expected.put(CommandHandler.class.getSimpleName(), expectedCommandHandlerAttributes);

        assertEquals(expected, HandlerAttributesUtils.handlerAttributes(messageHandlingMember));
    }

    @Test
    void testHandlerAttributesOnAnnotatedAllowReplayAndEventHandler() throws NoSuchMethodException {
        Method messageHandlingMember = AnnotatedAllowReplayAndEventHandler.class.getMethod("handle", Object.class);

        HandlerAttributes expected = new HandlerAttributes();
        Map<String, Object> expectedAllowReplayAttributes = new HashMap<>();
        expectedAllowReplayAttributes.put("allowReplay", true);
        expected.put(AllowReplay.class.getSimpleName(), expectedAllowReplayAttributes);
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", EventMessage.class);
        expectedMessageHandlerAttributes.put("payloadType", Boolean.class);
        expected.put(MessageHandler.class.getSimpleName(), expectedMessageHandlerAttributes);
        Map<String, Object> expectedEventHandlerAttributes = new HashMap<>();
        expectedEventHandlerAttributes.put("payloadType", Boolean.class);
        expected.put(EventHandler.class.getSimpleName(), expectedEventHandlerAttributes);

        assertEquals(expected, HandlerAttributesUtils.handlerAttributes(messageHandlingMember));
    }

    /**
     * Added as test since an {@link ExceptionHandler} is meta-annotated with {@link ResultHandler} and {@link
     * MessageHandlerInterceptor}. The former of these is in turn meta-annotated with {@link HasHandlerAttributes},
     * whilst the other is meta-annotated with {@link MessageHandler} (which too is meta-annotated with {@code
     * HasHandlerAttributes}. In such a set up <b>all</b> meta-annotations which are {@code HasHandlerAttributes} should
     * have their attributes returned, which thus should be validated to work.
     */
    @Test
    void testHandlerAttributesOnAnnotatedExceptionHandler() throws NoSuchMethodException {
        Method messageHandlingMember = AnnotatedExceptionHandler.class.getMethod("handle", Object.class);

        HandlerAttributes expected = new HandlerAttributes();
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", Message.class);
        expectedMessageHandlerAttributes.put("payloadType", Object.class);
        expected.put(MessageHandler.class.getSimpleName(), expectedMessageHandlerAttributes);
        Map<String, Object> expectedMessageHandlerInterceptorAttributes = new HashMap<>();
        expectedMessageHandlerInterceptorAttributes.put("messageType", Message.class);
        expectedMessageHandlerInterceptorAttributes.put("payloadType", Object.class);
        expected.put(MessageHandlerInterceptor.class.getSimpleName(), expectedMessageHandlerInterceptorAttributes);
        Map<String, Object> expectedResultHandlerAttributes = new HashMap<>();
        expectedResultHandlerAttributes.put("resultType", Exception.class);
        expected.put(ResultHandler.class.getSimpleName(), expectedResultHandlerAttributes);
        Map<String, Object> expectedExceptionHandlerAttributes = new HashMap<>();
        expectedExceptionHandlerAttributes.put("resultType", Exception.class);
        expectedExceptionHandlerAttributes.put("messageType", Message.class);
        expectedExceptionHandlerAttributes.put("payloadType", Object.class);
        expected.put(ExceptionHandler.class.getSimpleName(), expectedExceptionHandlerAttributes);

        assertEquals(expected, HandlerAttributesUtils.handlerAttributes(messageHandlingMember));
    }

    @Test
    void testHandlerAttributesOnAnnotatedCustomCommandHandler() throws NoSuchMethodException {
        Method messageHandlingMember = AnnotatedCustomCommandHandler.class.getMethod("handle", Object.class);

        HandlerAttributes expected = new HandlerAttributes();
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", CommandMessage.class);
        expectedMessageHandlerAttributes.put("payloadType", Long.class);
        expected.put(MessageHandler.class.getSimpleName(), expectedMessageHandlerAttributes);
        Map<String, Object> expectedCommandHandlerAttributes = new HashMap<>();
        expectedCommandHandlerAttributes.put("commandName", "custom-custom-name");
        expectedCommandHandlerAttributes.put("routingKey", "custom-routing-key");
        expectedCommandHandlerAttributes.put("payloadType", Long.class);
        expected.put(CommandHandler.class.getSimpleName(), expectedCommandHandlerAttributes);
        Map<String, Object> expectedCustomCommandHandlerAttributes = new HashMap<>();
        expectedCustomCommandHandlerAttributes.put("additionalAttribute", 42);
        expectedCustomCommandHandlerAttributes.put("payloadType", Long.class);
        expected.put(CustomCommandHandler.class.getSimpleName(), expectedCustomCommandHandlerAttributes);

        assertEquals(expected, HandlerAttributesUtils.handlerAttributes(messageHandlingMember));
    }

    @Test
    void testHandlerAttributesOnAnnotatedAnnotatedCustomCombinedHandlerWithAttributes() throws NoSuchMethodException {
        Method messageHandlingMember =
                AnnotatedCustomCombinedHandlerWithAttributes.class.getMethod("handle", Object.class);

        HandlerAttributes expected = new HandlerAttributes();
        Map<String, Object> expectedMessageHandlerAttributes = new HashMap<>();
        expectedMessageHandlerAttributes.put("messageType", DeadlineMessage.class);
        expectedMessageHandlerAttributes.put("payloadType", Float.class);
        expected.put(MessageHandler.class.getSimpleName(), expectedMessageHandlerAttributes);
        Map<String, Object> expectedCustomCustomCombinedHandlerWithAttributes = new HashMap<>();
        expectedCustomCustomCombinedHandlerWithAttributes.put("additionalAttribute", 42);
        expected.put(CustomCombinedHandlerWithAttributes.class.getSimpleName(),
                     expectedCustomCustomCombinedHandlerWithAttributes);

        assertEquals(expected, HandlerAttributesUtils.handlerAttributes(messageHandlingMember));
    }

    private static class AnnotatedCommandHandler {

        @SuppressWarnings("unused")
        @CommandHandler(
                commandName = "my-command",
                routingKey = "my-routing-key",
                payloadType = String.class
        )
        public void handle(Object command) {
            // No-op
        }
    }

    private static class AnnotatedAllowReplayAndEventHandler {

        @SuppressWarnings("unused")
        @AllowReplay
        @EventHandler(payloadType = Boolean.class)
        public void handle(Object event) {
            // No-op
        }
    }

    private static class AnnotatedExceptionHandler {

        @SuppressWarnings("unused")
        @ExceptionHandler
        public void handle(Object event) {
            // No-op
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @CommandHandler(commandName = "custom-custom-name", routingKey = "custom-routing-key")
    public @interface CustomCommandHandler {

        int additionalAttribute();

        Class<?> payloadType();
    }

    private static class AnnotatedCustomCommandHandler {

        @SuppressWarnings("unused")
        @CustomCommandHandler(
                additionalAttribute = 42,
                payloadType = Long.class
        )
        public void handle(Object command) {
            // No-op
        }
    }

    @HasHandlerAttributes
    @Retention(RetentionPolicy.RUNTIME)
    @MessageHandler(messageType = DeadlineMessage.class, payloadType = Float.class)
    public @interface CustomCombinedHandlerWithAttributes {

        int additionalAttribute();
    }

    private static class AnnotatedCustomCombinedHandlerWithAttributes {

        @SuppressWarnings("unused")
        @CustomCombinedHandlerWithAttributes(additionalAttribute = 42)
        public void handle(Object command) {
            // No-op
        }
    }
}