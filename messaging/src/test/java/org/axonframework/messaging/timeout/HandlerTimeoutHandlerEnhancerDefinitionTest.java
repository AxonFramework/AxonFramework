package org.axonframework.messaging.timeout;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.messaging.MessageHandlerTimeout;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class HandlerTimeoutHandlerEnhancerDefinitionTest {

    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;
    private HandlerTimeoutHandlerEnhancerDefinition handlerEnhancerDefinition;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();

        handlerEnhancerDefinition = new HandlerTimeoutHandlerEnhancerDefinition(new HandlerTimeoutConfiguration(
                new TaskTimeoutSettings(40000, 34000, 4000),
                new TaskTimeoutSettings(30000, 24000, 3000),
                new TaskTimeoutSettings(20000, 14000, 2000),
                new TaskTimeoutSettings(10000, 4000, 1000)
        ));
    }

    @Test
    void createsCorrectHandlerEnhancerDefinitionForQueryHandlerWithAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<QueryHandlerWithAnnotation> handler = getHandler(QueryHandlerWithAnnotation.class,
                                                                               "handle");
        MessageHandlingMember<QueryHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 100, 50, 10);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }


    @Test
    void createsCorrectHandlerEnhancerDefinitionForQueryHandlerWithoutAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<QueryHandlerWithAnnotation> handler = getHandler(QueryHandlerWithAnnotation.class,
                                                                               "handleDefault");
        MessageHandlingMember<QueryHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 20000, 14000, 2000);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }



    @Test
    void createsCorrectHandlerEnhancerDefinitionForCommandHandlerWithAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<CommandHandlerWithAnnotation> handler = getHandler(CommandHandlerWithAnnotation.class,
                                                                               "handle");
        MessageHandlingMember<CommandHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 100, 50, 10);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }


    @Test
    void createsCorrectHandlerEnhancerDefinitionForCommandHandlerWithoutAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<CommandHandlerWithAnnotation> handler = getHandler(CommandHandlerWithAnnotation.class,
                                                                               "handleDefault");
        MessageHandlingMember<CommandHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 30000, 24000, 3000);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }



    @Test
    void createsCorrectHandlerEnhancerDefinitionForEventHandlerWithAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<EventHandlerWithAnnotation> handler = getHandler(EventHandlerWithAnnotation.class,
                                                                               "handle");
        MessageHandlingMember<EventHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 100, 50, 10);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }


    @Test
    void createsCorrectHandlerEnhancerDefinitionForEventHandlerWithoutAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<EventHandlerWithAnnotation> handler = getHandler(EventHandlerWithAnnotation.class,
                                                                               "handleDefault");
        MessageHandlingMember<EventHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 40000, 34000, 4000);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }



    @Test
    void createsCorrectHandlerEnhancerDefinitionForDeadlineHandlerWithAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<DeadlineHandlerWithAnnotation> handler = getHandler(DeadlineHandlerWithAnnotation.class,
                                                                               "handle");
        MessageHandlingMember<DeadlineHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 100, 50, 10);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }


    @Test
    void createsCorrectHandlerEnhancerDefinitionForDeadlineHandlerWithoutAnnotation() throws NoSuchMethodException {
        MessageHandlingMember<DeadlineHandlerWithAnnotation> handler = getHandler(DeadlineHandlerWithAnnotation.class,
                                                                               "handleDefault");
        MessageHandlingMember<DeadlineHandlerWithAnnotation> result = handlerEnhancerDefinition.wrapHandler(handler);

        assertIsWrappedAndAssert(result, 10000, 4000, 1000);

        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, result);
    }

    private void assertIsWrappedAndAssert(MessageHandlingMember<?> handler, int timeout, int warningThreshold,
                                          int warningInterval) {
        assertInstanceOf(TimeoutWrappedMessageHandlingMember.class, handler);

        TimeoutWrappedMessageHandlingMember<?> castResult = (TimeoutWrappedMessageHandlingMember<?>) handler;
        assertEquals(timeout, castResult.getTimeout());
        assertEquals(warningThreshold, castResult.getWarningThreshold());
        assertEquals(warningInterval, castResult.getWarningInterval());
    }

    private <T> MessageHandlingMember<T> getHandler(Class<T> targetClass, String methodName)
            throws NoSuchMethodException {
        return handlerDefinition.createHandler(targetClass,
                                               targetClass.getDeclaredMethod(methodName, String.class),
                                               parameterResolver).get();
    }

    public static class QueryHandlerWithAnnotation {

        @MessageHandlerTimeout(timeout = 100, warningThreshold = 50, warningInterval = 10)
        @QueryHandler
        public void handle(String message) {
        }

        @QueryHandler
        public void handleDefault(String message) {
        }
    }


    public static class EventHandlerWithAnnotation {

        @MessageHandlerTimeout(timeout = 100, warningThreshold = 50, warningInterval = 10)
        @EventHandler
        public void handle(String message) {
        }

        @EventHandler
        public void handleDefault(String message) {
        }
    }


    public static class CommandHandlerWithAnnotation {

        @MessageHandlerTimeout(timeout = 100, warningThreshold = 50, warningInterval = 10)
        @CommandHandler
        public void handle(String message) {
        }

        @CommandHandler
        public void handleDefault(String message) {
        }
    }


    public static class DeadlineHandlerWithAnnotation {

        @MessageHandlerTimeout(timeout = 100, warningThreshold = 50, warningInterval = 10)
        @DeadlineHandler
        public void handle(String message) {
        }

        @DeadlineHandler
        public void handleDefault(String message) {
        }
    }
}