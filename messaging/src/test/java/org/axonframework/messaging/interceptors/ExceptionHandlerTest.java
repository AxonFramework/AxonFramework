/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.interceptors;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.MessageHandlerInterceptorMemberChain;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ExceptionHandler} for message handling components.
 *
 * @author Steven van Beelen
 */
class ExceptionHandlerTest {

    private static final String COMMAND_HANDLER_INVOKED = "command";
    private static final String EVENT_HANDLER_INVOKED = "event";
    private static final String QUERY_HANDLER_INVOKED = "query";

    private AtomicReference<String> invokedHandler;
    private AtomicBoolean handleAllInvoked;
    private Set<String> invokedExceptionHandlers;

    private ExceptionHandlingComponent messageHandlingComponent;
    private AnnotatedHandlerInspector<ExceptionHandlingComponent> inspector;

    @BeforeEach
    void setUp() {
        invokedHandler = new AtomicReference<>();
        handleAllInvoked = new AtomicBoolean();
        invokedExceptionHandlers = spy(new HashSet<>());

        messageHandlingComponent =
                new ExceptionHandlingComponent(invokedHandler, handleAllInvoked, invokedExceptionHandlers);
        inspector = AnnotatedHandlerInspector.inspectType(ExceptionHandlingComponent.class);
    }

    @Test
    void exceptionHandlerIsInvokedForAnCommandHandlerThrowingAnException() {
        CommandMessage<SomeCommand> command =
                asCommandMessage(new SomeCommand(() -> new RuntimeException("some-exception")));

        try {
            Object result = handle(command);
            assertNull(result);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        assertEquals(COMMAND_HANDLER_INVOKED, invokedHandler.get());
        assertTrue(handleAllInvoked.get());
        assertTrue(invokedExceptionHandlers.contains("leastSpecificExceptionHandler"));
    }

    @Test
    void exceptionHandlerIsInvokedForAnEventHandlerThrowingAnException() {
        EventMessage<SomeEvent> event =
                asEventMessage(new SomeEvent(() -> new RuntimeException("some-exception")));

        try {
            Object result = handle(event);
            assertNull(result);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        assertEquals(EVENT_HANDLER_INVOKED, invokedHandler.get());
        assertTrue(handleAllInvoked.get());
        assertTrue(invokedExceptionHandlers.contains("leastSpecificExceptionHandler"));
    }

    @Test
    void exceptionHandlerIsInvokedForAnQueryHandlerThrowingAnException() {
        QueryMessage<SomeQuery, SomeQueryResponse> query = new GenericQueryMessage<>(
                new SomeQuery(() -> new RuntimeException("some-exception")),
                ResponseTypes.instanceOf(SomeQueryResponse.class)
        );

        try {
            Object result = handle(query);
            assertNull(result);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        assertEquals(QUERY_HANDLER_INVOKED, invokedHandler.get());
        assertTrue(handleAllInvoked.get());
        assertTrue(invokedExceptionHandlers.contains("leastSpecificExceptionHandler"));
    }

    @Test
    void exceptionHandlersAreInvokedInHandlerPriorityOrder() {
        CommandMessage<SomeCommand> command =
                asCommandMessage(new SomeCommand(() -> new IllegalStateException("some-exception")));

        try {
            Object result = handle(command);
            assertNull(result);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        assertEquals(COMMAND_HANDLER_INVOKED, invokedHandler.get());
        assertTrue(handleAllInvoked.get());

        InOrder exceptionHandlerOrder = inOrder(invokedExceptionHandlers);
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleIllegalStateExceptionForSomeCommand");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleExceptionForSomeCommand");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleExceptionForSomeCommandThroughAnnotation");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleIllegalStateExceptionForSomeCommandThroughAnnotation");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleIllegalStateException");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("handleIllegalStateExceptionThroughAnnotation");
        exceptionHandlerOrder.verify(invokedExceptionHandlers)
                             .add("leastSpecificExceptionHandler");
    }

    /**
     * This method is a similar approach as followed by the
     * {@link org.axonframework.eventhandling.AnnotationEventHandlerAdapter#handle(EventMessage)}. Thus, mirroring
     * regular message handling components.
     */
    private Object handle(Message<?> message) throws Exception {
        Optional<MessageHandlingMember<? super ExceptionHandlingComponent>> handler =
                inspector.getHandlers(ExceptionHandlingComponent.class)
                         .filter(h -> h.canHandle(message))
                         .findFirst();
        if (handler.isPresent()) {
            MessageHandlerInterceptorMemberChain<ExceptionHandlingComponent> interceptorChain =
                    inspector.chainedInterceptor(ExceptionHandlingComponent.class);
            return interceptorChain.handle(message, messageHandlingComponent, handler.get());
        }
        return null;
    }

    @SuppressWarnings("unused") // suppress not-invoked exception handler warning.
    private static class ExceptionHandlingComponent {

        private final AtomicReference<String> invokedHandler;
        private final AtomicBoolean handleAllInvoked;
        private final Set<String> invokedExceptionHandlers;

        private ExceptionHandlingComponent(AtomicReference<String> invokedHandler,
                                           AtomicBoolean handleAllInvoked,
                                           Set<String> invokedExceptionHandlers) {
            this.invokedHandler = invokedHandler;
            this.handleAllInvoked = handleAllInvoked;
            this.invokedExceptionHandlers = invokedExceptionHandlers;
        }

        @ExceptionHandler
        public void leastSpecificExceptionHandler() {
            handleAllInvoked.set(true);
            invokedExceptionHandlers.add("leastSpecificExceptionHandler");
            throw new IllegalStateException("leastSpecificExceptionHandler");
        }

        @ExceptionHandler(resultType = IllegalStateException.class)
        public void handleRuntimeExceptionThroughAnnotation() {
            invokedExceptionHandlers.add("handleIllegalStateExceptionThroughAnnotation");
            throw new IllegalStateException("handleIllegalStateExceptionThroughAnnotation");
        }

        @ExceptionHandler
        public void handleIllegalStateException(IllegalStateException exception) {
            invokedExceptionHandlers.add("handleIllegalStateException");
            throw exception;
        }

        @ExceptionHandler(
                resultType = IllegalStateException.class,
                payloadType = SomeCommand.class
        )
        public void handleIllegalStateExceptionForSomeCommandThroughAnnotation() {
            invokedExceptionHandlers.add("handleIllegalStateExceptionForSomeCommandThroughAnnotation");
            throw new IllegalStateException("handleIllegalStateExceptionForSomeCommandThroughAnnotation");
        }

        @ExceptionHandler(payloadType = SomeCommand.class)
        public void handleExceptionForSomeCommandThroughAnnotation() {
            invokedExceptionHandlers.add("handleExceptionForSomeCommandThroughAnnotation");
            throw new IllegalStateException("handleExceptionForSomeCommandThroughAnnotation");
        }

        @ExceptionHandler
        public void handleExceptionForSomeCommand(SomeCommand command) {
            invokedExceptionHandlers.add("handleExceptionForSomeCommand");
            throw new IllegalStateException("handleExceptionForSomeCommand");
        }

        @ExceptionHandler
        public void handleRuntimeExceptionForSomeCommand(SomeCommand command, IllegalStateException exception) {
            invokedExceptionHandlers.add("handleIllegalStateExceptionForSomeCommand");
            throw exception;
        }

        @CommandHandler
        public void handle(SomeCommand command) throws Exception {
            invokedHandler.set(COMMAND_HANDLER_INVOKED);
            throw command.exceptionSupplier.get();
        }

        @EventHandler
        public void on(SomeEvent event) throws Exception {
            invokedHandler.set(EVENT_HANDLER_INVOKED);
            throw event.exceptionSupplier.get();
        }

        @QueryHandler
        public SomeQueryResponse handle(SomeQuery query) throws Exception {
            invokedHandler.set(QUERY_HANDLER_INVOKED);
            throw query.exceptionSupplier.get();
        }
    }

    private static class SomeCommand {

        private final Supplier<Exception> exceptionSupplier;

        private SomeCommand(Supplier<Exception> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }
    }

    private static class SomeEvent {

        private final Supplier<Exception> exceptionSupplier;

        private SomeEvent(Supplier<Exception> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }
    }

    private static class SomeQuery {

        private final Supplier<Exception> exceptionSupplier;

        private SomeQuery(Supplier<Exception> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }
    }

    private static class SomeQueryResponse {

    }
}
