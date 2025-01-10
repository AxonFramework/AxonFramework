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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Playground to test the new message handling configuration.
 */
class NewMessageHandlerRegistrationTest {

    private static final QualifiedName MESSAGE_HANDLER_NAME = new QualifiedName("axon", "message", "0.0.1");
    private static final QualifiedName COMMAND_HANDLER_NAME = new QualifiedName("axon", "command", "0.0.1");
    private static final QualifiedName EVENT_HANDLER_NAME = new QualifiedName("axon", "event", "0.0.1");
    private static final QualifiedName QUERY_HANDLER_NAME = new QualifiedName("axon", "query", "0.0.1");

    private AtomicBoolean messageHandlerInvoked;
    private AtomicBoolean commandHandlerInvoked;
    private AtomicBoolean eventHandlerInvoked;
    private AtomicBoolean queryHandlerInvoked;

    private GenericMessageHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        messageHandlerInvoked = new AtomicBoolean(false);
        commandHandlerInvoked = new AtomicBoolean(false);
        eventHandlerInvoked = new AtomicBoolean(false);
        queryHandlerInvoked = new AtomicBoolean(false);

        testSubject = new GenericMessageHandlingComponent();

        testSubject.subscribe(MESSAGE_HANDLER_NAME, (message, context) -> {
                       messageHandlerInvoked.set(true);
                       return MessageStream.empty();
                   })
                   .subscribeCommandHandler(COMMAND_HANDLER_NAME, (command, context) -> {
                       commandHandlerInvoked.set(true);
                       return MessageStream.empty();
                   })
                   .subscribeEventHandler(EVENT_HANDLER_NAME, (event, context) -> {
                       eventHandlerInvoked.set(true);
                       return MessageStream.empty();
                   })
                   .subscribeQueryHandler(QUERY_HANDLER_NAME, (query, context) -> {
                       queryHandlerInvoked.set(true);
                       return MessageStream.empty();
                   });
    }

    // TODO parameterized test to validate archetypes
    @Test
    void subscribingHandlers() {
        MessageHandlingComponent<MessageHandler<?, ?>, Message<?>, Message<?>> plainMHC = new GenericMessageHandlingComponent();
        CommandModelComponent aggregate = new CommandModelComponent();
        QueryModelComponent projector = new QueryModelComponent();
        GenericMessageHandlingComponent genericMHC = new GenericMessageHandlingComponent();

        QualifiedName testName = new QualifiedName("axon", "test", "0.0.1");

        plainMHC.subscribe(testName, (message, context) -> MessageStream.empty())

                .subscribe(testName, new TestCommandHandler())
//                .subscribCommandHandler(testName, new TestCommandHandler())
//                .subscribCommandHandler(testName, (command, context) -> MessageStream.empty())

                .subscribe(testName, new TestEventHandler())
//                .subscribEventHandler(testName, new TestEventHandler())
//                .subscribEventHandler(testName, (event, context) -> MessageStream.empty())

                .subscribe(testName, new TestQueryHandler())
//                .subscribQueryHandler(testName, new TestQueryHandler())
//                .subscribQueryHandler(testName, (query, context) -> MessageStream.empty())

                .subscribe(Set.of(testName), new TestMessageHandlingComponent<>());

        aggregate/*.subscribe(testName, (message, context) -> MessageStream.empty())*/

                 .subscribe(testName, new TestCommandHandler())
                 .subscribeCommandHandler(testName, new TestCommandHandler())
                 .subscribeCommandHandler(testName, (command, context) -> MessageStream.empty())

                 .subscribe(testName, new TestEventHandler())
                 .subscribeEventHandler(testName, new TestEventHandler())
                 .subscribeEventHandler(testName, (event, context) -> MessageStream.empty())

//                 .subscribMessageHandler(testName, new TestQueryHandler())
//                 .subscribQueryHandler(testName, new TestQueryHandler())
//                 .subscribQueryHandler(testName, (query, context) -> MessageStream.empty())

                 /*.subscribe(Set.of(testName), new TestMessageHandlingComponent<>())*/;

        projector/*.subscribe(testName, (message, context) -> MessageStream.empty())*/

//                 .subscribMessageHandler(testName, new TestCommandHandler())
//                 .subscribCommandHandler(testName, new TestCommandHandler())
//                 .subscribCommandHandler(testName, (command, context) -> MessageStream.empty())

                 .subscribe(testName, new TestEventHandler())
                 .subscribeEventHandler(testName, new TestEventHandler())
                 .subscribeEventHandler(testName, (event, context) -> MessageStream.empty())

                 .subscribe(testName, new TestQueryHandler())
                 .subscribeQueryHandler(testName, new TestQueryHandler())
                 .subscribeQueryHandler(testName, (query, context) -> MessageStream.empty())

                 /*.subscribe(Set.of(testName), new TestMessageHandlingComponent<>())*/;

        genericMHC.subscribe(testName, (message, context) -> MessageStream.empty())

                  .subscribe(testName, new TestCommandHandler())
                  .subscribeCommandHandler(testName, new TestCommandHandler())
                  .subscribeCommandHandler(testName, (command, context) -> MessageStream.empty())

                  .subscribe(testName, new TestEventHandler())
                  .subscribeEventHandler(testName, new TestEventHandler())
                  .subscribeEventHandler(testName, (event, context) -> MessageStream.empty())

                  .subscribe(testName, new TestQueryHandler())
                  .subscribeQueryHandler(testName, new TestQueryHandler())
                  .subscribeQueryHandler(testName, (query, context) -> MessageStream.empty())

                  .subscribe(Set.of(testName), new TestMessageHandlingComponent<>());
    }

    @Test
    void handlingUnknownMessageReturnsFailedMessageStream() {
        MessageStream<? extends Message<?>> result = testSubject.handle(new GenericMessage<>(new QualifiedName("axon",
                                                                                                               "test",
                                                                                                               "0.0.1"),
                                                                                             "test"),
                                                                        ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.firstAsCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertTrue(resultFuture.isCompletedExceptionally());

        assertFalse(messageHandlerInvoked.get());
        assertFalse(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingGenericMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        Message<Object> testMessage = new MessageWithType(MESSAGE_HANDLER_NAME);

        MessageStream<? extends Message<?>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.firstAsCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertTrue(messageHandlerInvoked.get());
        assertFalse(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingCommandMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        Message<Object> testMessage = new CommandMessageWithType(COMMAND_HANDLER_NAME);

        MessageStream<? extends Message<?>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.firstAsCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertFalse(messageHandlerInvoked.get());
        assertTrue(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingEventMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        Message<Object> testMessage = new EventMessageWithType(EVENT_HANDLER_NAME);

        MessageStream<? extends Message<?>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.firstAsCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertFalse(messageHandlerInvoked.get());
        assertFalse(commandHandlerInvoked.get());
        assertTrue(eventHandlerInvoked.get());
        assertFalse(queryHandlerInvoked.get());
    }

    @Test
    void handlingQueryMessageReturnsExpectedMessageStream() throws ExecutionException, InterruptedException {
        Message<Object> testMessage = new QueryMessageWithType(QUERY_HANDLER_NAME);

        MessageStream<? extends Message<?>> result = testSubject.handle(testMessage, ProcessingContext.NONE);

        CompletableFuture<? extends Entry<? extends Message<?>>> resultFuture = result.firstAsCompletableFuture();

        assertTrue(resultFuture.isDone());
        assertFalse(resultFuture.isCompletedExceptionally());
        Entry<? extends Message<?>> resultEntry = resultFuture.get();
        assertNull(resultEntry);

        assertFalse(messageHandlerInvoked.get());
        assertFalse(commandHandlerInvoked.get());
        assertFalse(eventHandlerInvoked.get());
        assertTrue(queryHandlerInvoked.get());
    }

    @Test
    void subscribingMessageHandlingComponentEnsuresMessageDelegation() {
        Message<Object> testMessage = new MessageWithType(MESSAGE_HANDLER_NAME);
        CommandMessage<Object> testCommandMessage = new CommandMessageWithType(COMMAND_HANDLER_NAME);
        EventMessage<Object> testEventMessage = new EventMessageWithType(EVENT_HANDLER_NAME);
        QueryMessage<Object, Object> testQueryMessage = new QueryMessageWithType(QUERY_HANDLER_NAME);

        GenericMessageHandlingComponent testSubjectWithRegisteredMHC =
                new GenericMessageHandlingComponent().subscribe(testSubject.supportedMessages(), testSubject);

        testSubjectWithRegisteredMHC.handle(testMessage, ProcessingContext.NONE);
        testSubjectWithRegisteredMHC.handle(testCommandMessage, ProcessingContext.NONE);
        testSubjectWithRegisteredMHC.handle(testEventMessage, ProcessingContext.NONE);
        testSubjectWithRegisteredMHC.handle(testQueryMessage, ProcessingContext.NONE);

        assertTrue(messageHandlerInvoked.get());
        assertTrue(commandHandlerInvoked.get());
        assertTrue(eventHandlerInvoked.get());
        assertTrue(queryHandlerInvoked.get());
    }

    private static class TestCommandHandler implements CommandHandler {

        @Override
        @Nonnull
        public MessageStream<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                             @Nonnull ProcessingContext context) {
            return MessageStream.just(new GenericCommandResultMessage<>(
                    new QualifiedName("axon", "command-response", "0.0.1"), "done!"
            ));
        }
    }

    private static class TestEventHandler implements EventHandler {

        @Override
        @Nonnull
        public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                               @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }
    }

    private static class TestQueryHandler implements QueryHandler {

        @Nonnull
        @Override
        public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> message,
                                                             @Nonnull ProcessingContext context) {
            return MessageStream.fromIterable(Sets.newSet(
                    new GenericQueryResponseMessage<>(new QualifiedName("test", "query-response", "0.0.1"), "one"),
                    new GenericQueryResponseMessage<>(new QualifiedName("test", "query-response", "0.0.1"), "two"),
                    new GenericQueryResponseMessage<>(new QualifiedName("test", "query-response", "0.0.1"), "three")
            ));
        }
    }

    private static class TestMessageHandlingComponent<HH extends MessageHandler<?, ?>, HM extends Message<?>, HR extends Message<?>>
            implements MessageHandlingComponent<HH, HM, HR> {

        @Override
        public MessageHandlerRegistry<HH> subscribe(@Nonnull Set<QualifiedName> names,
                                                    @Nonnull HH messageHandler) {
            return null;
        }

        @Nonnull
        @Override
        public MessageStream<HR> handle(@Nonnull HM message, @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }

        @Override
        public Set<QualifiedName> supportedMessages() {
            return Set.of();
        }
    }

    private record MessageWithType(QualifiedName name) implements Message<Object> {

        @Override
        public String getIdentifier() {
            return "identifier - MessageWithQualifiedName";
        }

        @Nonnull
        @Override
        public QualifiedName name() {
            return name;
        }

        @Override
        public Object getPayload() {
            return "payload - MessageWithQualifiedName";
        }

        @Override
        public MetaData getMetaData() {
            return MetaData.emptyInstance();
        }

        @Override
        public Class<Object> getPayloadType() {
            throw new UnsupportedOperationException("we shouldn't be using this anymore");
        }

        @Override
        public Message<Object> withMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public Message<Object> andMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }
    }

    private record CommandMessageWithType(QualifiedName name) implements CommandMessage<Object> {

        @Override
        public String getIdentifier() {
            return "identifier - CommandMessageWithQualifiedName";
        }

        @Nonnull
        @Override
        public QualifiedName name() {
            return name;
        }

        @Override
        public String getCommandName() {
            return "commandName - CommandMessageWithQualifiedName";
        }

        @Override
        public Object getPayload() {
            return "payload - CommandMessageWithQualifiedName";
        }

        @Override
        public MetaData getMetaData() {
            return MetaData.emptyInstance();
        }

        @Override
        public Class<Object> getPayloadType() {
            throw new UnsupportedOperationException("we shouldn't be using this anymore");
        }

        @Override
        public CommandMessage<Object> withMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public CommandMessage<Object> andMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public <C> CommandMessage<C> withConvertedPayload(@Nonnull Function<Object, C> conversion) {
            return null;
        }
    }

    private record EventMessageWithType(QualifiedName name) implements EventMessage<Object> {

        @Override
        public String getIdentifier() {
            return "identifier - EventMessageWithQualifiedName";
        }

        @Nonnull
        @Override
        public QualifiedName name() {
            return name;
        }

        @Override
        public Object getPayload() {
            return "payload - EventMessageWithQualifiedName";
        }

        @Override
        public MetaData getMetaData() {
            return MetaData.emptyInstance();
        }

        @Override
        public Instant getTimestamp() {
            return Instant.now();
        }

        @Override
        public Class<Object> getPayloadType() {
            throw new UnsupportedOperationException("we shouldn't be using this anymore");
        }

        @Override
        public EventMessage<Object> withMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public EventMessage<Object> andMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public <C> EventMessage<C> withConvertedPayload(@Nonnull Function<Object, C> conversion) {
            return null;
        }
    }

    private record QueryMessageWithType(QualifiedName name) implements QueryMessage<Object, Object> {

        @Override
        public String getIdentifier() {
            return "identifier - QueryMessageWithQualifiedName";
        }

        @Nonnull
        @Override
        public QualifiedName name() {
            return name;
        }

        @Override
        public Object getPayload() {
            return "payload - QueryMessageWithQualifiedName";
        }

        @Override
        public MetaData getMetaData() {
            return MetaData.emptyInstance();
        }

        @Override

        public Class<Object> getPayloadType() {
            throw new UnsupportedOperationException("we shouldn't be using this anymore");
        }

        @Override
        public String getQueryName() {
            return "queryName - QueryMessageWithQualifiedName";
        }

        @Override
        public ResponseType<Object> getResponseType() {
            return null;
        }

        @Override
        public QueryMessage<Object, Object> withMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public QueryMessage<Object, Object> andMetaData(@Nonnull Map<String, ?> metaData) {
            return this;
        }

        @Override
        public <C> QueryMessage<C, Object> withConvertedPayload(@Nonnull Function<Object, C> conversion) {
            return null;
        }
    }
}