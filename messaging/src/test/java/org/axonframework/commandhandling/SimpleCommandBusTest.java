/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.common.StubExecutor;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.messaging.QualifiedNameUtils.fromClassName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class SimpleCommandBusTest {

    private SimpleCommandBus testSubject;
    private StubExecutor executor;

    @BeforeEach
    void setUp() {
        this.executor = new StubExecutor();
        this.testSubject = new SimpleCommandBus(executor);
    }

    @AfterEach
    void tearDown() {
        assertFalse(CurrentUnitOfWork.isStarted());
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void dispatchCommandHandlerSubscribed() throws Exception {
        testSubject.subscribe(String.class.getName(), new StubCommandHandler("Hi!"));
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(asCommandMessage("Say hi!"),
                                                                              ProcessingContext.NONE);
        assertEquals("Hi!",
                     actual.get().getPayload());
    }


    @Test
    void dispatchCommandImplicitUnitOfWorkIsCommittedOnReturnValue() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(String.class.getName(), new MessageHandler<CommandMessage<?>, CommandResultMessage<?>>() {
            @Override
            public Object handleSync(CommandMessage<?> command) throws Exception {
                return command;
            }

            @Override
            public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                 ProcessingContext processingContext) {
                unitOfWork.set(processingContext);
                return MessageStream.just(GenericCommandResultMessage.asCommandResultMessage(message));
            }
        });
        var actual = testSubject.dispatch(asCommandMessage("Say hi!"), ProcessingContext.NONE);
        assertTrue(actual.isDone());
        assertFalse(actual.isCompletedExceptionally());
        Message<?> actualResult = actual.join();
        assertEquals("Say hi!", actualResult.getPayload());
        assertNotNull(unitOfWork.get());
    }

    @Test
    void dispatchCommandImplicitUnitOfWorkIsRolledBackOnException() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(String.class.getName(), new MessageHandler<>() {
            @Override
            public Object handleSync(CommandMessage<?> command) {
                throw new RuntimeException();
            }

            @Override
            public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                 ProcessingContext processingContext) {
                unitOfWork.set(processingContext);
                throw new RuntimeException();
            }
        });
        testSubject.dispatch(asCommandMessage("Say hi!"), ProcessingContext.NONE);
        assertTrue(unitOfWork.get().isError());
    }

    @Test
    void dispatchCommandNoHandlerSubscribed() {
        CommandMessage<Object> command = asCommandMessage("test");
        var result = testSubject.dispatch(command, ProcessingContext.NONE);

        assertTrue(result.isCompletedExceptionally());
        CompletionException actualException = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(NoHandlerForCommandException.class, actualException.getCause());
    }

    @Test
    void dispatchCommandHandlerUnsubscribed() {
        StubCommandHandler commandHandler = new StubCommandHandler("Not important");
        Registration subscription = testSubject.subscribe(String.class.getName(), commandHandler);
        subscription.cancel();
        CommandMessage<Object> command = asCommandMessage("Say hi!");
        var actual = testSubject.dispatch(command, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException actualException = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(NoHandlerForCommandException.class,
                         actualException.getCause());
    }

    @Test
    void asyncHandlerCompletion() throws Exception {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(String.class.getName(), new StubCommandHandler(ourFutureIsBright));

        var actual = testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"),
                                          ProcessingContext.NONE);

        assertFalse(actual.isDone());
        CompletableFuture<String> stringCompletableFuture = actual.thenApply(crm -> Thread.currentThread().getName());

        Thread t = new Thread(() -> ourFutureIsBright.complete("42"));
        t.start();
        t.join();

        assertTrue(stringCompletableFuture.isDone());
        assertEquals(t.getName(), stringCompletableFuture.get());
    }

    @Test
    void asyncHandlerVirtual() throws Exception {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(String.class.getName(), new StubCommandHandler(ourFutureIsBright));

        var actual = testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"),
                                          ProcessingContext.NONE);

        assertFalse(actual.isDone());
        CompletableFuture<String> stringCompletableFuture = actual.thenApply(crm -> Thread.currentThread().getName());

        Thread t = Thread.startVirtualThread(() -> ourFutureIsBright.complete("42"));
        t.join();

        assertTrue(stringCompletableFuture.isDone());
        assertEquals(t.getName(), stringCompletableFuture.get());
    }

    @Test
    void handlerInvokedOnExecutorThread() {
        executor.enqueueTasks();

        var commandHandler = spy(new StubCommandHandler("ok"));
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.subscribe(command.getCommandName(), commandHandler);

        var actual = testSubject.dispatch(command, ProcessingContext.NONE);

        verify(commandHandler, never()).handle(eq(command), any());
        assertFalse(actual.isDone());

        executor.runAll();

        verify(commandHandler).handle(eq(command), any());
        assertTrue(actual.isDone());
    }

    @Test
    void exceptionThrownFromHandlerReturnedInCompletableFuture() {
        var commandHandler = new StubCommandHandler("ok") {
            @Override
            public MessageStream<? extends Message<?>> handle(CommandMessage<?> command,
                                                              ProcessingContext processingContext) {
                throw new MockException("Simulating exception");
            }
        };
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.subscribe(command.getCommandName(), commandHandler);

        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(
                command, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(MockException.class, exception.getCause());
        assertEquals("Simulating exception", exception.getCause().getMessage());
    }

    @Test
    void exceptionalStreamFromHandlerReturnedInCompletableFuture() {
        var commandHandler = new StubCommandHandler(new MockException("Simulating exception"));
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.subscribe(command.getCommandName(), commandHandler);

        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(
                command, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(MockException.class, exception.getCause());
        assertEquals("Simulating exception", exception.getCause().getMessage());
    }

    @Test
    void exceptionIsThrownWhenNoHandlerIsRegistered() {
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(
                asCommandMessage(new Object()),
                ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(NoHandlerForCommandException.class, exception.getCause());
    }

    @Test
    void lifecycleHandlersAreInvokedOnEachInvocation() {
        ProcessingLifecycleHandlerRegistrar lifecycleHandlerRegistrar = mock(ProcessingLifecycleHandlerRegistrar.class);
        testSubject = new SimpleCommandBus(executor, List.of(lifecycleHandlerRegistrar));

        var commandHandler = new StubCommandHandler("ok");
        CommandMessage<Object> command = asCommandMessage(new Object());
        testSubject.subscribe(command.getCommandName(), commandHandler);

        verify(lifecycleHandlerRegistrar, never()).registerHandlers(any());

        testSubject.dispatch(command, ProcessingContext.NONE);

        verify(lifecycleHandlerRegistrar).registerHandlers(any());

        testSubject.dispatch(command, ProcessingContext.NONE);

        verify(lifecycleHandlerRegistrar, times(2)).registerHandlers(notNull());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        var handler1 = mock(MessageHandler.class);
        var handler2 = mock(MessageHandler.class);
        testSubject.subscribe("test1", handler1);
        assertThrows(DuplicateCommandHandlerSubscriptionException.class,
                     () -> testSubject.subscribe("test1", handler2));
    }

    @Test
    void duplicateRegistrationForSameHandlerIsAllowed() {
        var handler = mock(MessageHandler.class);
        testSubject.subscribe("test1", handler);
        assertDoesNotThrow(() -> testSubject.subscribe("test1", handler));
    }

    @Test
    void describeReturnsRegisteredComponents() {
        ProcessingLifecycleHandlerRegistrar lifecycleHandlerRegistrar = mock(ProcessingLifecycleHandlerRegistrar.class);
        testSubject = new SimpleCommandBus(executor, lifecycleHandlerRegistrar);
        var handler1 = mock(MessageHandler.class);
        var handler2 = mock(MessageHandler.class);
        testSubject.subscribe("test1", handler1);
        testSubject.subscribe("test2", handler2);

        ComponentDescriptor mockComponentDescriptor = mock(ComponentDescriptor.class);
        testSubject.describeTo(mockComponentDescriptor);

        verify(mockComponentDescriptor).describeProperty("worker", executor);
        verify(mockComponentDescriptor).describeProperty("lifecycleRegistrars", List.of(lifecycleHandlerRegistrar));
        verify(mockComponentDescriptor).describeProperty("subscriptions", Map.of("test1", handler1, "test2", handler2));
    }

    private static class StubCommandHandler implements MessageHandler<CommandMessage<?>, Message<?>> {

        private final Object result;

        public StubCommandHandler(Object result) {
            this.result = result;
        }

        @Override
        public MessageStream<? extends Message<?>> handle(CommandMessage<?> command,
                                                          ProcessingContext processingContext) {
            if (result instanceof Throwable error) {
                return MessageStream.failed(error);
            } else if (result instanceof CompletableFuture<?> future) {
                return MessageStream.fromFuture(future.thenApply(
                        r -> new GenericMessage<>(fromClassName(r.getClass()), r)
                ));
            } else {
                return MessageStream.just(new GenericMessage<>(fromClassName(result.getClass()), result));
            }
        }

        @Override
        public Object handleSync(CommandMessage<?> message) {
            throw new UnsupportedOperationException("handleSync should not be invoked");
        }
    }
}
