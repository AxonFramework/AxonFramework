/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test to validate the {@link AnnotationCommandHandlerAdapter}.
 *
 * @author Allard Buijze
 */
class AnnotationCommandHandlerAdapterTest {

    private static final MessageType TEST_TYPE = new MessageType("command");

    private CommandBus mockBus;
    private MyCommandHandler mockTarget;
    private UnitOfWork<CommandMessage<?>> mockUnitOfWork;

    private AnnotationCommandHandlerAdapter<MyCommandHandler> testSubject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockBus = mock(CommandBus.class);
        mockTarget = new MyCommandHandler();

        ParameterResolverFactory parameterResolverFactory = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new AnnotationCommandHandlerAdapter<>(mockTarget, parameterResolverFactory);

        mockUnitOfWork = mock(UnitOfWork.class);
        when(mockUnitOfWork.resources()).thenReturn(mock(Map.class));
        when(mockUnitOfWork.getCorrelationData()).thenReturn(MetaData.emptyInstance());
        when(mockBus.subscribe(any(QualifiedName.class), any())).thenReturn(mockBus);
        CurrentUnitOfWork.set(mockUnitOfWork);
    }

    @AfterEach
    void tearDown() {
        CurrentUnitOfWork.clear(mockUnitOfWork);
    }

    @Test
    void handlerDispatchingVoidReturnType() {
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_TYPE, "");

        Object result = testSubject.handle(testCommand, mock(ProcessingContext.class))
                                   .firstAsCompletableFuture()
                                   .join()
                                   .message()
                                   .getPayload();

        assertNull(result);
        assertEquals(1, mockTarget.voidHandlerInvoked);
        assertEquals(0, mockTarget.returningHandlerInvoked);
    }

    @Test
    void handlerDispatchingWithReturnType() {
        CommandMessage<Long> testCommand = new GenericCommandMessage<>(TEST_TYPE, 1L);

        Object result = testSubject.handle(testCommand, mock(ProcessingContext.class))
                                   .firstAsCompletableFuture()
                                   .join()
                                   .message()
                                   .getPayload();

        assertEquals(1L, result);
        assertEquals(0, mockTarget.voidHandlerInvoked);
        assertEquals(1, mockTarget.returningHandlerInvoked);
    }

    @Test
    void handlerDispatchingWithCustomCommandName() {
        CommandMessage<Long> testCommand =
                new GenericCommandMessage<>(new GenericMessage<>(TEST_TYPE, 1L), "almostLong");

        Object result = testSubject.handle(testCommand, mock(ProcessingContext.class))
                                   .firstAsCompletableFuture()
                                   .join()
                                   .message()
                                   .getPayload();

        assertEquals(1L, result);
        assertEquals(0, mockTarget.voidHandlerInvoked);
        assertEquals(0, mockTarget.returningHandlerInvoked);
        assertEquals(1, mockTarget.almostDuplicateReturningHandlerInvoked);
    }

    @Test
    void handlerDispatchingThrowingException() {
        try {
            testSubject.handle(new GenericCommandMessage<>(TEST_TYPE, new HashSet<>()), mock(ProcessingContext.class))
                       .firstAsCompletableFuture()
                       .join();

            fail("Expected exception");
        } catch (Exception ex) {
            assertEquals(Exception.class, ex.getCause().getClass());
            return;
        }
        fail("Shouldn't make it till here");
    }

    @Test
    void subscribe() {
        testSubject.subscribe(mockBus);

        verify(mockBus).subscribe(new QualifiedName(Long.class), testSubject);
        verify(mockBus).subscribe(new QualifiedName(String.class), testSubject);
        verify(mockBus).subscribe(new QualifiedName(HashSet.class), testSubject);
        verify(mockBus).subscribe(new QualifiedName(ArrayList.class), testSubject);
        verify(mockBus).subscribe(new QualifiedName("almostLong"), testSubject);
        verifyNoMoreInteractions(mockBus);
    }

    @Test
    void handleNoHandlerForCommand() {
        CommandMessage<Object> command = new GenericCommandMessage<>(TEST_TYPE, new LinkedList<>());

        assertThrows(NoHandlerForCommandException.class,
                     () -> testSubject.handle(command, mock(ProcessingContext.class)));
    }

    @Test
    @Disabled("TODO This now has a MessageStream instead a MessageStream as the result")
    void messageHandlerInterceptorAnnotatedMethodsAreSupportedForCommandHandlingComponents() {
        CommandMessage<String> testCommandMessage = new GenericCommandMessage<>(TEST_TYPE, "");
        List<CommandMessage<?>> withInterceptor = new ArrayList<>();
        List<CommandMessage<?>> withoutInterceptor = new ArrayList<>();
        mockTarget = new MyInterceptingCommandHandler(withoutInterceptor, withInterceptor, new ArrayList<>());
        testSubject = new AnnotationCommandHandlerAdapter<>(mockTarget);

        Object result = testSubject.handle(testCommandMessage, mock(ProcessingContext.class))
                                   .firstAsCompletableFuture()
                                   .join()
                                   .message()
                                   .getPayload();

        assertNull(result);
        assertEquals(1, mockTarget.voidHandlerInvoked);
        assertEquals(Collections.singletonList(testCommandMessage), withInterceptor);
        assertEquals(Collections.singletonList(testCommandMessage), withoutInterceptor);
    }

    @Test
    @Disabled("TODO #3062 - Exception Handler support")
    void exceptionHandlerAnnotatedMethodsAreSupportedForCommandHandlingComponents() {
        CommandMessage<List<?>> testCommandMessage = new GenericCommandMessage<>(TEST_TYPE, new ArrayList<>());
        List<Exception> interceptedExceptions = new ArrayList<>();
        mockTarget = new MyInterceptingCommandHandler(new ArrayList<>(), new ArrayList<>(), interceptedExceptions);
        testSubject = new AnnotationCommandHandlerAdapter<>(mockTarget);

        try {
            testSubject.handle(testCommandMessage, mock(ProcessingContext.class));
            fail("Expected exception to be thrown");
        } catch (Exception e) {

        }

        assertFalse(interceptedExceptions.isEmpty());
        assertEquals(1, interceptedExceptions.size());
        Exception interceptedException = interceptedExceptions.getFirst();
        assertInstanceOf(RuntimeException.class, interceptedException);
        assertEquals("Some exception", interceptedException.getMessage());
    }

    @SuppressWarnings("unused")
    private static class MyCommandHandler {

        private int voidHandlerInvoked;
        private int returningHandlerInvoked;
        private int almostDuplicateReturningHandlerInvoked;

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void myVoidHandler(String stringCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            voidHandlerInvoked++;
        }

        @CommandHandler(commandName = "almostLong")
        public Long myAlmostDuplicateReturningHandler(Long longCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            assertNotNull(unitOfWork, "The UnitOfWork was not passed to the command handler");
            almostDuplicateReturningHandlerInvoked++;
            return longCommand;
        }

        @CommandHandler
        public Long myReturningHandler(Long longCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            assertNotNull(unitOfWork, "The UnitOfWork was not passed to the command handler");
            returningHandlerInvoked++;
            return longCommand;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void exceptionThrowingHandler(HashSet<Object> o) throws Exception {
            throw new Exception("Some exception");
        }

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void exceptionThrowingHandler(ArrayList<Object> o) {
            throw new RuntimeException("Some exception");
        }
    }

    @SuppressWarnings("unused")
    private static class MyInterceptingCommandHandler extends MyCommandHandler {

        private final List<CommandMessage<?>> interceptedWithoutInterceptorChain;
        private final List<CommandMessage<?>> interceptedWithInterceptorChain;
        private final List<Exception> interceptedExceptions;

        private MyInterceptingCommandHandler(List<CommandMessage<?>> interceptedWithoutInterceptorChain,
                                             List<CommandMessage<?>> interceptedWithInterceptorChain,
                                             List<Exception> interceptedExceptions) {
            this.interceptedWithoutInterceptorChain = interceptedWithoutInterceptorChain;
            this.interceptedWithInterceptorChain = interceptedWithInterceptorChain;
            this.interceptedExceptions = interceptedExceptions;
        }

        @MessageHandlerInterceptor
        public void interceptAny(CommandMessage<?> command) {
            interceptedWithoutInterceptorChain.add(command);
        }

        @MessageHandlerInterceptor
        public Object interceptAny(CommandMessage<?> command, InterceptorChain chain) throws Exception {
            interceptedWithInterceptorChain.add(command);
            return chain.proceedSync();
        }

        @ExceptionHandler
        public void handle(Exception exception) {
            interceptedExceptions.add(exception);
        }
    }
}
