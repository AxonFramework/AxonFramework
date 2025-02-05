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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.StubExecutor;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleCommandBus}.
 *
 * @author Allard Buijze
 */
class SimpleCommandBusTest {

    private static final QualifiedName COMMAND_NAME = new QualifiedName("axon", "test", "5.0.0");
    private static final String PAYLOAD = "Say hi!";
    private static final CommandMessage<String> TEST_COMMAND =
            new GenericCommandMessage<>(new MessageType("command"), PAYLOAD);

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
        testSubject.subscribe(COMMAND_NAME, new StubCommandHandler("Hi!"));

        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

        assertEquals("Hi!", actual.get().getPayload());
    }

    @Test
    void dispatchCommandImplicitUnitOfWorkIsCommittedOnReturnValue() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(COMMAND_NAME,
                              (message, processingContext) -> {
                                  unitOfWork.set(processingContext);
                                  return MessageStream.just(asCommandResultMessage(message));
                              });
        var actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);
        assertTrue(actual.isDone());
        assertFalse(actual.isCompletedExceptionally());
        Message<?> actualResult = actual.join();
        assertEquals(PAYLOAD, actualResult.getPayload());
        assertNotNull(unitOfWork.get());
    }

    @Test
    void dispatchCommandImplicitUnitOfWorkIsRolledBackOnException() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(COMMAND_NAME,
                              (message, processingContext) -> {
                                  unitOfWork.set(processingContext);
                                  throw new RuntimeException();
                              });
        testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);
        assertTrue(unitOfWork.get().isError());
    }

    @Test
    void dispatchCommandNoHandlerSubscribed() {
        var result = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

        assertTrue(result.isCompletedExceptionally());
        CompletionException actualException = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(NoHandlerForCommandException.class, actualException.getCause());
    }

    @Test
    @Disabled("TODO Investigation on registration")
    void dispatchCommandHandlerUnsubscribed() {
        StubCommandHandler commandHandler = new StubCommandHandler("Not important");
        testSubject.subscribe(COMMAND_NAME, commandHandler);
        //TODO Investigation on registration
//        subscription.cancel();

        var actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException actualException = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(NoHandlerForCommandException.class,
                         actualException.getCause());
    }

    @Test
    void asyncHandlerCompletion() throws Exception {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(COMMAND_NAME, new StubCommandHandler(ourFutureIsBright));

        var actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

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
        testSubject.subscribe(COMMAND_NAME, new StubCommandHandler(ourFutureIsBright));

        var actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

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
        CommandMessage<String> command = TEST_COMMAND;
        testSubject.subscribe(command.name(), commandHandler);

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
            public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                           @Nonnull ProcessingContext processingContext) {
                throw new MockException("Simulating exception");
            }
        };
        CommandMessage<String> command = TEST_COMMAND;
        testSubject.subscribe(command.name(), commandHandler);

        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(command, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(MockException.class, exception.getCause());
        assertEquals("Simulating exception", exception.getCause().getMessage());
    }

    @Test
    void exceptionalStreamFromHandlerReturnedInCompletableFuture() {
        var commandHandler = new StubCommandHandler(new MockException("Simulating exception"));
        CommandMessage<String> command = TEST_COMMAND;
        testSubject.subscribe(command.name(), commandHandler);

        CompletableFuture<? extends Message<?>> actual =
                testSubject.dispatch(command, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(MockException.class, exception.getCause());
        assertEquals("Simulating exception", exception.getCause().getMessage());
    }

    @Test
    void exceptionIsThrownWhenNoHandlerIsRegistered() {
        CompletableFuture<? extends Message<?>> actual = testSubject.dispatch(TEST_COMMAND, ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, actual::get);
        assertInstanceOf(NoHandlerForCommandException.class, exception.getCause());
    }

    @Test
    void lifecycleHandlersAreInvokedOnEachInvocation() {
        ProcessingLifecycleHandlerRegistrar lifecycleHandlerRegistrar = mock(ProcessingLifecycleHandlerRegistrar.class);
        testSubject = new SimpleCommandBus(executor, List.of(lifecycleHandlerRegistrar));

        var commandHandler = new StubCommandHandler("ok");
        CommandMessage<String> command = TEST_COMMAND;
        testSubject.subscribe(command.name(), commandHandler);

        verify(lifecycleHandlerRegistrar, never()).registerHandlers(any());

        testSubject.dispatch(command, ProcessingContext.NONE);

        verify(lifecycleHandlerRegistrar).registerHandlers(any());

        testSubject.dispatch(command, ProcessingContext.NONE);

        verify(lifecycleHandlerRegistrar, times(2)).registerHandlers(notNull());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        var handler1 = mock(CommandHandler.class);
        var handler2 = mock(CommandHandler.class);
        testSubject.subscribe(COMMAND_NAME, handler1);
        assertThrows(DuplicateCommandHandlerSubscriptionException.class,
                     () -> testSubject.subscribe(COMMAND_NAME, handler2));
    }

    @Test
    void duplicateRegistrationForSameHandlerIsAllowed() {
        var handler = mock(CommandHandler.class);
        testSubject.subscribe(COMMAND_NAME, handler);
        assertDoesNotThrow(() -> testSubject.subscribe(COMMAND_NAME, handler));
    }

    @Test
    void describeReturnsRegisteredComponents() {
        ProcessingLifecycleHandlerRegistrar lifecycleHandlerRegistrar = mock(ProcessingLifecycleHandlerRegistrar.class);
        testSubject = new SimpleCommandBus(executor, lifecycleHandlerRegistrar);
        var handler1 = mock(CommandHandler.class);
        var handler2 = mock(CommandHandler.class);
        testSubject.subscribe(COMMAND_NAME, handler1);
        QualifiedName handleTwoName = new QualifiedName("axon", "test2", "5.0.0");
        testSubject.subscribe(handleTwoName, handler2);

        ComponentDescriptor mockComponentDescriptor = mock(ComponentDescriptor.class);
        testSubject.describeTo(mockComponentDescriptor);

        verify(mockComponentDescriptor).describeProperty("worker", executor);
        verify(mockComponentDescriptor).describeProperty("lifecycleRegistrars", List.of(lifecycleHandlerRegistrar));
        verify(mockComponentDescriptor)
                .describeProperty("subscriptions", Map.of(COMMAND_NAME, handler1, handleTwoName, handler2));
    }

    private static class StubCommandHandler implements CommandHandler {

        private final Object result;

        public StubCommandHandler(Object result) {
            this.result = result;
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                       @Nonnull ProcessingContext processingContext) {
            if (result instanceof Throwable error) {
                return MessageStream.failed(error);
            } else if (result instanceof CompletableFuture<?> future) {
                return MessageStream.fromFuture(future.thenApply(
                        r -> new GenericMessage<>(new MessageType(r.getClass()), r)
                ));
            } else {
                return MessageStream.just(new GenericMessage<>(new MessageType(result.getClass()), result));
            }
        }
    }

    private static GenericCommandResultMessage<?> asCommandResultMessage(CommandMessage<?> message) {
        var payload = message.getPayload();
        return new GenericCommandResultMessage<>(new MessageType(payload.getClass()), payload);
    }
}
