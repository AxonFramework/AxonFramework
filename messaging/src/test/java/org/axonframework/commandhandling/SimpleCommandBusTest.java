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

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class SimpleCommandBusTest {

    private SimpleCommandBus testSubject;

    @BeforeEach
    void setUp() {
        this.testSubject = new SimpleCommandBus();
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void dispatchCommandHandlerSubscribed() throws Exception {
        testSubject.subscribe(String.class.getName(), new MyStringCommandHandler());
        CompletableFuture<? extends CommandResultMessage<?>> actual = testSubject.dispatch(asCommandMessage("Say hi!"),
                                                                                           ProcessingContext.NONE);
        assertEquals("Say hi!",
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
        CommandResultMessage<?> actualResult = actual.join();
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
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
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
        testSubject.subscribe(String.class.getName(), new MyStringAsyncCommandHandler(ourFutureIsBright));

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
        testSubject.subscribe(String.class.getName(), new MyStringAsyncCommandHandler(ourFutureIsBright));

        var actual = testSubject.dispatch(GenericCommandMessage.asCommandMessage("some-string"),
                                          ProcessingContext.NONE);

        assertFalse(actual.isDone());
        CompletableFuture<String> stringCompletableFuture = actual.thenApply(crm -> Thread.currentThread().getName());

        Thread t = Thread.startVirtualThread(() -> ourFutureIsBright.complete("42"));
        t.join();

        assertTrue(stringCompletableFuture.isDone());
        assertEquals(t.getName(), stringCompletableFuture.get());
    }

    private static class MyStringCommandHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        @Override
        public MessageStream<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                       ProcessingContext processingContext) {
            return MessageStream.just(new GenericCommandResultMessage<>(message.getPayload()));
        }

        @Override
        public Object handleSync(CommandMessage<?> message) {
            return message.getPayload();
        }
    }

    private static class MyStringAsyncCommandHandler
            implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final CompletableFuture<?> result;


        private MyStringAsyncCommandHandler(CompletableFuture<?> result) {
            this.result = result;
        }

        @Override
        public Object handleSync(CommandMessage<?> message) {
            return null;
        }

        @Override
        public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                             ProcessingContext processingContext) {
            return MessageStream.fromFuture(result.thenApply(GenericCommandResultMessage::asCommandResultMessage));
        }
    }
}
