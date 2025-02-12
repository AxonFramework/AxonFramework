package org.axonframework.messaging.timeout;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutWrappedMessageHandlingMemberTest {

    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Test
    void interruptsMessageHandlingMemberAsConfigured() throws NoSuchMethodException {
        MessageHandlingMember<TestMessageHandler> original = handlerDefinition.createHandler(TestMessageHandler.class,
                                                                                             TestMessageHandler.class.getDeclaredMethod(
                                                                                                     "handle",
                                                                                                     String.class),
                                                                                             parameterResolver).get();

        TimeoutWrappedMessageHandlingMember<TestMessageHandler> wrappedHandler = new TimeoutWrappedMessageHandlingMember<>(
                original, 300, 200, 50
        );

        assertThrows(TimeoutException.class,
                     () -> wrappedHandler.handle(GenericEventMessage.asEventMessage("my-message"),
                                                 new TestMessageHandler()));
    }

    public static class TestMessageHandler {

        @EventHandler
        public void handle(String message) throws InterruptedException {
            Thread.sleep(500);
        }
    }
}