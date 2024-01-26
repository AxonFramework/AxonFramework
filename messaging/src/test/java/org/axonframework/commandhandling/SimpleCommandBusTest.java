/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.Registration;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class SimpleCommandBusTest {

    private TestSpanFactory spanFactory;
    private SimpleCommandBus testSubject;
    private DefaultCommandBusSpanFactory commandBusSpanFactory;

    @BeforeEach
    void setUp() {
        this.spanFactory = new TestSpanFactory();
        this.commandBusSpanFactory = DefaultCommandBusSpanFactory.builder().spanFactory(spanFactory).build();
        this.testSubject = SimpleCommandBus.builder()
                                           .spanFactory(commandBusSpanFactory).build();
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void dispatchCommandHandlerSubscribed() {
        testSubject.subscribe(String.class.getName(), new MyStringCommandHandler());
        testSubject.dispatch(asCommandMessage("Say hi!"),
                             (CommandCallback<String, CommandMessage<String>>) (command, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                     commandResultMessage.optionalExceptionResult()
                                                         .ifPresent(Throwable::printStackTrace);
                                     fail("Did not expect exception");
                                 }
                                 assertEquals("Say hi!", commandResultMessage.getPayload().getPayload());
                             });
    }

    @Test
    void dispatchIsCorrectlyTraced() {
        testSubject.subscribe(String.class.getName(), new MyStringCommandHandler());
        testSubject.dispatch(asCommandMessage("Say hi!"),
                             (CommandCallback<String, CommandMessage<String>>) (command, commandResultMessage) -> {
                                 spanFactory.verifySpanActive("CommandBus.dispatchCommand");
                                 spanFactory.verifySpanPropagated("CommandBus.dispatchCommand", command);
                                 spanFactory.verifySpanCompleted("CommandBus.handleCommand");
                             });
        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
    }

    @Test
    void dispatchIsCorrectlyTracedDuringException() {
//        testSubject.setRollbackConfiguration(RollbackConfigurationType.UNCHECKED_EXCEPTIONS);
        testSubject.subscribe(String.class.getName(), command -> {
            throw new RuntimeException("Some exception");
        });
        testSubject.dispatch(asCommandMessage("Say hi!"),
                             (CommandCallback<String, CommandMessage<String>>) (command, commandResultMessage) -> {
                                 spanFactory.verifySpanPropagated("CommandBus.dispatchCommand", command);
                                 spanFactory.verifySpanCompleted("CommandBus.handleCommand");
                             });
        spanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
        spanFactory.verifySpanHasException("CommandBus.dispatchCommand", RuntimeException.class);
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
        CompletableFuture<CommandResultMessage<Object>> actual = testSubject.dispatch(asCommandMessage("Say hi!"),
                                                                                      ProcessingContext.NONE);
        assertTrue(actual.isDone());
        assertFalse(actual.isCompletedExceptionally());
        CommandResultMessage<Object> actualResult = actual.join();
        assertEquals("Say hi!", actualResult.getPayload());

        assertFalse(unitOfWork.get().isError());
        assertTrue(unitOfWork.get().isCommitted());
        assertTrue(unitOfWork.get().isCompleted());
    }

    @Test
    void dispatchCommandImplicitUnitOfWorkIsRolledBackOnException() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(String.class.getName(), new MessageHandler<CommandMessage<?>, CommandResultMessage<?>>() {
            @Override
            public Object handleSync(CommandMessage<?> command) throws Exception {
                throw new RuntimeException();
            }

            @Override
            public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                     ProcessingContext processingContext) {
                unitOfWork.set(processingContext);
                throw new RuntimeException();
            }
        });
        testSubject.dispatch(asCommandMessage("Say hi!"), (commandMessage, commandResultMessage) -> {
            if (commandResultMessage.isExceptional()) {
                Throwable cause = commandResultMessage.exceptionResult();
                assertEquals(RuntimeException.class, cause.getClass());
            } else {
                fail("Expected exception");
            }
        });
        assertTrue(unitOfWork.get().isError());
    }

    @Test
        // TODO - Remove this test. Annotated Handler adapters should be responsible to convers checked exceptions to a "regular" result if UoW is to be committed
    void dispatchCommandUnitOfWorkIsCommittedOnCheckedException() {
        final AtomicReference<ProcessingContext> unitOfWork = new AtomicReference<>();
        testSubject.subscribe(String.class.getName(), new MessageHandler<>() {
            @Override
            public Object handleSync(CommandMessage<?> command) throws Exception {
                throw new Exception();
            }

            @Override
            public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                     ProcessingContext processingContext) {
                unitOfWork.set(processingContext);
                return MessageStream.failed(new MockException("Simulating failure"));
            }
        });
//        testSubject.setRollbackConfiguration(RollbackConfigurationType.UNCHECKED_EXCEPTIONS);

        testSubject.dispatch(asCommandMessage("Say hi!"), (commandMessage, commandResultMessage) -> {
            if (commandResultMessage.isExceptional()) {
                Throwable cause = commandResultMessage.exceptionResult();
                assertEquals(Exception.class, cause.getClass());
            } else {
                fail("Expected exception");
            }
        });
        assertTrue(unitOfWork.get().isError());
        assertTrue(!unitOfWork.get().isCommitted());
    }

    @Test
    void dispatchCommandNoHandlerSubscribed() {
        CommandMessage<Object> command = asCommandMessage("test");
        CompletableFuture<CommandResultMessage<Object>> result = testSubject.dispatch(command, ProcessingContext.NONE);

        assertTrue(result.isCompletedExceptionally());
        CompletionException actualException = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(NoHandlerForCommandException.class, actualException.getCause());
    }

    private CommandCallback createCallbackMock() {
        CommandCallback mock = mock(CommandCallback.class);
        when(mock.wrap(any())).thenCallRealMethod();
        return mock;
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchCommandHandlerUnsubscribed() {
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
        Registration subscription = testSubject.subscribe(String.class.getName(), commandHandler);
        subscription.close();
        CommandMessage<Object> command = asCommandMessage("Say hi!");
        CompletableFuture<CommandResultMessage<Object>> actual = testSubject.dispatch(command,
                                                                                      ProcessingContext.NONE);

        assertTrue(actual.isCompletedExceptionally());
        CompletionException actualException = assertThrows(CompletionException.class, actual::get);
        assertInstanceOf(NoHandlerForCommandException.class,
                         actualException.getCause());
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchCommandNoHandlerSubscribedCallsMonitorCallbackIgnored() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        MessageMonitor<? super CommandMessage<?>> messageMonitor = (message) -> new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                fail("Expected #reportFailure");
            }

            @Override
            public void reportFailure(Throwable cause) {
                countDownLatch.countDown();
            }

            @Override
            public void reportIgnored() {
                fail("Expected #reportFailure");
            }
        };

        testSubject = SimpleCommandBus.builder().messageMonitor(messageMonitor).build();

        try {
            testSubject.dispatch(asCommandMessage("test"), createCallbackMock());
        } catch (NoHandlerForCommandException expected) {
            // ignore
        }

        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    void interceptorChainCommandHandledSuccessfully() throws Exception {
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor1 = mock(MessageHandlerInterceptor.class);
        final MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor2 = mock(MessageHandlerInterceptor.class);
        final MessageHandler<CommandMessage<?>, CommandResultMessage<?>> commandHandler = mock(MessageHandler.class);
        when(mockInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockInterceptor2.handle(
                        (UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0],
                        (InterceptorChain) invocation.getArguments()[1]));
        when(mockInterceptor2.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> commandHandler
                        .handleSync(((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0]).getMessage()));
        testSubject.registerHandlerInterceptor(mockInterceptor1);
        testSubject.registerHandlerInterceptor(mockInterceptor2);
        when(commandHandler.handleSync(isA(CommandMessage.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class.getName(), commandHandler);

        testSubject.dispatch(asCommandMessage("Hi there!"),
                             (commandMessage, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                     Throwable cause = commandResultMessage.exceptionResult();
                                     throw new RuntimeException("Unexpected exception", cause);
                                 }
                                 assertEquals("Hi there!", commandResultMessage.getPayload());
                             });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handleSync(isA(GenericCommandMessage.class));
    }

    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    @Test
    void interceptorChainCommandHandlerThrowsException() throws Exception {
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor1 = mock(MessageHandlerInterceptor.class);
        final MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor2 = mock(MessageHandlerInterceptor.class);
        final MessageHandler<CommandMessage<?>, CommandResultMessage<?>> commandHandler = mock(MessageHandler.class);
        when(mockInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockInterceptor2.handle(
                        (UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0],
                        (InterceptorChain) invocation.getArguments()[1]));
        when(mockInterceptor2.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> commandHandler
                        .handleSync(((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0]).getMessage()));

        testSubject.registerHandlerInterceptor(mockInterceptor1);
        testSubject.registerHandlerInterceptor(mockInterceptor2);
        when(commandHandler.handleSync(isA(CommandMessage.class)))
                .thenThrow(new RuntimeException("Faking failed command handling"));
        testSubject.subscribe(String.class.getName(), commandHandler);

        testSubject.dispatch(asCommandMessage("Hi there!"),
                             (commandMessage, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                     Throwable cause = commandResultMessage.exceptionResult();
                                     assertEquals("Faking failed command handling", cause.getMessage());
                                 } else {
                                     fail("Expected exception to be thrown");
                                 }
                             });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handleSync(isA(GenericCommandMessage.class));
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown", "unchecked"})
    @Test
    void interceptorChainInterceptorThrowsException() throws Exception {
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor1 =
                mock(MessageHandlerInterceptor.class, "stubName");
        final MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor2 = mock(MessageHandlerInterceptor.class);
        when(mockInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceedSync());
        testSubject.registerHandlerInterceptor(mockInterceptor1);
        testSubject.registerHandlerInterceptor(mockInterceptor2);
        MessageHandler<CommandMessage<?>, CommandResultMessage<?>> commandHandler = mock(MessageHandler.class);
        when(commandHandler.handleSync(isA(CommandMessage.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class.getName(), commandHandler);
        RuntimeException someException = new RuntimeException("Mocking");
        doThrow(someException).when(mockInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
        testSubject.dispatch(asCommandMessage("Hi there!"),
                             (commandMessage, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                     Throwable cause = commandResultMessage.exceptionResult();
                                     assertEquals("Mocking", cause.getMessage());
                                 } else {
                                     fail("Expected exception to be propagated");
                                 }
                             });
        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(
                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler, never()).handleSync(isA(CommandMessage.class));
    }

    @Test
    void commandReplyMessageCorrelationData() {
        testSubject.subscribe(String.class.getName(), message -> message.getPayload().toString());
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        CommandMessage<String> command = asCommandMessage("Hi");
        testSubject.dispatch(command, (CommandCallback<String, String>) (commandMessage, commandResultMessage) -> {
            if (commandResultMessage.isExceptional()) {
                fail("Command execution should be successful");
            }
            assertEquals(command.getIdentifier(), commandResultMessage.getMetaData().get("traceId"));
            assertEquals(command.getIdentifier(), commandResultMessage.getMetaData().get("correlationId"));
            assertEquals(command.getPayload(), commandResultMessage.getPayload());
        });
    }

    @Test
    void duplicateCommandHandlerResolverSetsTheExpectedHandler() {
        DuplicateCommandHandlerResolver testDuplicateCommandHandlerResolver = DuplicateCommandHandlerResolution.silentOverride();
        SimpleCommandBus testSubject =
                SimpleCommandBus.builder()
                                .duplicateCommandHandlerResolver(testDuplicateCommandHandlerResolver)
                                .build();

        MyStringCommandHandler initialHandler = spy(new MyStringCommandHandler());
        MyStringCommandHandler duplicateHandler = spy(new MyStringCommandHandler());
        CommandMessage<Object> testMessage = asCommandMessage("Say hi!");

        // Subscribe the initial handler
        testSubject.subscribe(String.class.getName(), initialHandler);
        // Then, subscribe a duplicate
        testSubject.subscribe(String.class.getName(), duplicateHandler);

        // And after dispatching a test command, it should be handled by the initial handler
        testSubject.dispatch(testMessage, ProcessingContext.NONE);

        verify(duplicateHandler).handleSync(testMessage);
        verify(initialHandler, never()).handleSync(testMessage);
    }

    @Test
    void asyncHandlerInvocation() {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(String.class.getName(), new MyStringAsyncCommandHandler(ourFutureIsBright));

        FutureCallback<String, String> callback = new FutureCallback<>();
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"), callback);

        assertFalse(callback.isDone());
        ourFutureIsBright.complete("42");
        assertTrue(callback.isDone());
        assertEquals("42", callback.getResult().getPayload());
    }

    @Test
    void asyncHandlerCompletion() throws Exception {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(String.class.getName(), new MyStringAsyncCommandHandler(ourFutureIsBright));

        FutureCallback<String, String> callback = new FutureCallback<>();
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"), callback);

        assertFalse(callback.isDone());
        CompletableFuture<String> stringCompletableFuture = callback.thenApply(crm -> Thread.currentThread().getName());

        Thread t = new Thread(() -> ourFutureIsBright.complete("42"));
        t.start();
        t.join();

        assertTrue(stringCompletableFuture.isDone());
        assertEquals(t.getName(), stringCompletableFuture.get());
    }

    @Test
    void asyncHandlerVirtual() throws Exception {
        var ourFutureIsBright = new CompletableFuture<>();
        testSubject.subscribe(String.class.getName(), new MyStringAsyncCommandHandler(ourFutureIsBright));

        FutureCallback<String, String> callback = new FutureCallback<>();
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"), callback);

        assertFalse(callback.isDone());
        CompletableFuture<String> stringCompletableFuture = callback.thenApply(crm -> Thread.currentThread().getName());

        Thread t = Thread.startVirtualThread(() -> ourFutureIsBright.complete("42"));
        t.join();

        assertTrue(stringCompletableFuture.isDone());
        assertEquals(t.getName(), stringCompletableFuture.get());
    }

    private static class MyStringCommandHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        @Override
        public Object handleSync(CommandMessage<?> message) {
            return message;
        }
    }

    private static class MyStringAsyncCommandHandler
            implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final CompletableFuture<?> result;


        private MyStringAsyncCommandHandler(CompletableFuture<?> result) {
            this.result = result;
        }

        @Override
        public Object handleSync(CommandMessage<?> message) throws Exception {
            return null;
        }

        @Override
        public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                 ProcessingContext processingContext) {
            return MessageStream.fromFuture(result.thenApply(GenericCommandResultMessage::asCommandResultMessage));
        }
    }
}
