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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class NewMessageHandlerRegistrationTest {

    private static final MessageType COMMAND_TYPE = new MessageType("command");
    private static final MessageType EVENT_TYPE = new MessageType("event");
    private static final MessageType QUERY_TYPE = new MessageType("query");

    private AtomicBoolean commandHandlerInvoked;
    private AtomicBoolean eventHandlerInvoked;
    private AtomicBoolean queryHandlerInvoked;

    private GenericMessageHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        commandHandlerInvoked = new AtomicBoolean(false);
        eventHandlerInvoked = new AtomicBoolean(false);
        queryHandlerInvoked = new AtomicBoolean(false);

        testSubject = new GenericMessageHandlingComponent();

        testSubject.subscribe(COMMAND_TYPE.qualifiedName(), (CommandHandler) (command, context) -> {
                       commandHandlerInvoked.set(true);
                       return MessageStream.empty().cast();
                   })
                   .subscribe(EVENT_TYPE.qualifiedName(), (EventHandler) (event, context1) -> {
                       eventHandlerInvoked.set(true);
                       return MessageStream.empty().cast();
                   })
                   .subscribe(QUERY_TYPE.qualifiedName(), (QueryHandler) (event1, context2) -> {
                       queryHandlerInvoked.set(true);
                       return MessageStream.empty().cast();
                   });
    }

    // TODO parameterized test to validate archetypes
    @Test
    void subscribingHandlers() {
        MessageHandlingComponent plainMHC = new GenericMessageHandlingComponent();
        CommandModelComponent aggregate = new CommandModelComponent();
        QueryModelComponent projector = new QueryModelComponent();
        GenericMessageHandlingComponent genericMHC = new GenericMessageHandlingComponent();

        QualifiedName testName = new QualifiedName("test");

        plainMHC.subscribe(testName, new TestCommandHandler())
                .subscribe(testName, new TestEventHandler())
                .subscribe(testName, new TestQueryHandler())
                .subscribe(Set.of(testName), (MessageHandler) new TestMessageHandlingComponent());

        aggregate.subscribe(testName, new TestCommandHandler())
                 .subscribe(testName, (CommandHandler) (command, context) -> MessageStream.empty().cast())
                 .subscribe(testName, new TestEventHandler())
                 .subscribe(testName, (EventHandler) (event, context) -> MessageStream.empty().cast());

        projector.subscribe(testName, new TestEventHandler())
                 .subscribe(testName, (EventHandler) (event, context) -> MessageStream.empty().cast())
                 .subscribe(testName, new TestQueryHandler())
                 .subscribe(testName, (QueryHandler) (query, context) -> MessageStream.empty().cast())

        /*.subscribe(Set.of(testName), new TestMessageHandlingComponent<>())*/;

        genericMHC.subscribe(testName, new TestCommandHandler())
                  .subscribe(testName, (CommandHandler) (command, context) -> MessageStream.empty().cast())
                  .subscribe(testName, new TestEventHandler())
                  .subscribe(testName, (EventHandler) (event, context) -> MessageStream.empty().cast())
                  .subscribe(testName, new TestQueryHandler())
                  .subscribe(testName, (QueryHandler) (query, context) -> MessageStream.empty().cast())
                  .subscribe(Set.of(testName), (MessageHandler) new TestMessageHandlingComponent());
    }

    @Test
    void handlingCommandMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        CommandMessage<Object> testMessage = new GenericCommandMessage<>(COMMAND_TYPE, COMMAND_TYPE);

        MessageStream<? extends Message<?>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.first().asCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertTrue(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingEventMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        EventMessage<?> testMessage = new GenericEventMessage<>(EVENT_TYPE, "payload");

        MessageStream.Empty<Message<Void>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.first().asCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertFalse(commandHandlerInvoked.get());
        assertTrue(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingQueryMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        QueryMessage<?, ?> testMessage =
                new GenericQueryMessage<>(QUERY_TYPE, "payload", ResponseTypes.instanceOf(String.class));

        MessageStream<? extends QueryResponseMessage<?>> result =
                testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.first().asCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertFalse(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertTrue(queryHandlerInvoked.get());
    }

    @Test
    void subscribingMessageHandlingComponentEnsuresMessageDelegation() {
        CommandMessage<?> testCommandMessage = new GenericCommandMessage<>(COMMAND_TYPE, COMMAND_TYPE);
        EventMessage<?> testEventMessage = new GenericEventMessage<>(EVENT_TYPE, "payload");
        QueryMessage<?, ?> testQueryMessage =
                new GenericQueryMessage<>(QUERY_TYPE, "payload", ResponseTypes.instanceOf(String.class));

        MessageHandlingComponent testSubjectWithRegisteredMHC =
                new GenericMessageHandlingComponent().subscribe(testSubject);

        testSubjectWithRegisteredMHC.handle(testCommandMessage, ProcessingContext.NONE);
        testSubjectWithRegisteredMHC.handle(testEventMessage, ProcessingContext.NONE);
        testSubjectWithRegisteredMHC.handle(testQueryMessage, ProcessingContext.NONE);

        assertTrue(commandHandlerInvoked.get());
        assertTrue(eventHandlerInvoked.get());
        assertTrue(queryHandlerInvoked.get());
    }

    private static class TestCommandHandler implements CommandHandler {

        @Override
        @Nonnull
        public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                              @Nonnull ProcessingContext context) {
            return MessageStream.just(new GenericCommandResultMessage<>(new MessageType("command-response"), "done!"));
        }
    }

    private static class TestEventHandler implements EventHandler {

        @Override
        @Nonnull
        public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                         @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }
    }

    private static class TestQueryHandler implements QueryHandler {

        private final MessageType responseType = new MessageType("query-response");

        @Nonnull
        @Override
        public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> message,
                                                             @Nonnull ProcessingContext context) {
            return MessageStream.fromIterable(Sets.newSet(
                    new GenericQueryResponseMessage<>(responseType, "one"),
                    new GenericQueryResponseMessage<>(responseType, "two"),
                    new GenericQueryResponseMessage<>(responseType, "three")
            ));
        }
    }

    private static class TestMessageHandlingComponent implements MessageHandlingComponent {

        @Override
        public MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull CommandHandler commandHandler) {
            return null;
        }

        @Override
        public MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull EventHandler eventHandler) {
            return null;
        }

        @Override
        public MessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull QueryHandler queryHandler) {
            return null;
        }

        @Override
        public Set<QualifiedName> supportedCommands() {
            return Set.of();
        }

        @Override
        public Set<QualifiedName> supportedEvents() {
            return Set.of();
        }

        @Override
        public Set<QualifiedName> supportedQueries() {
            return Set.of();
        }

        @Nonnull
        @Override
        public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                              @Nonnull ProcessingContext context) {
            return MessageStream.empty().cast();
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                         @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }

        @Nonnull
        @Override
        public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                             @Nonnull ProcessingContext context) {
            return MessageStream.empty().cast();
        }
    }
}