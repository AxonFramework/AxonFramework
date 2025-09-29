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
package org.axonframework.queryhandling.annotations;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.annotations.UnsupportedHandlerException;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.PassThroughConverter;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;

/**
 * Test class validating the {@link AnnotatedQueryHandlingComponent}.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
class AnnotatedQueryHandlingComponentTest {

    private AnnotatedQueryHandlingComponent<?> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotatedQueryHandlingComponent<>(new MyQueryHandler(),
                                                            PassThroughConverter.MESSAGE_INSTANCE);
    }

    @Test
    void subscribeFailsForHandlerWithInvalidParameters() {
        assertThatThrownBy(() -> new AnnotatedQueryHandlingComponent<>(
                new FaultyParameterOrderQueryHandler(), PassThroughConverter.MESSAGE_INSTANCE
        )).isInstanceOf(UnsupportedHandlerException.class);
    }

    @Test
    void subscribeFailsForHandlerWithVoidReturnType() {
        assertThatThrownBy(() -> new AnnotatedQueryHandlingComponent<>(
                new VoidReturnTypeQueryHandler(), PassThroughConverter.MESSAGE_INSTANCE
        )).isInstanceOf(UnsupportedHandlerException.class);
    }

    @Test
    void handleReturnsFaultyMessageStreamWithNoHandlerForQueryException() {
        // given...
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("unknown"), null, instanceOf(String.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then...
        assertThat(result.hasNextAvailable()).isFalse();
        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(NoHandlerForQueryException.class);
    }

    @Test
    void handleInvokesQueryHandlerOfConcreteTypeReturningMessageStreamWithSingleResponse() {
        // given...
        String expectedResponse = "hello";
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("echo"),
                                                         expectedResponse,
                                                         instanceOf(String.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then..
        assertThat(result.hasNextAvailable()).isTrue();
        Object responsePayload = result.first()
                                       .asCompletableFuture()
                                       .thenApply(MessageStream.Entry::message)
                                       .thenApply(Message::payload)
                                       .join();
        assertThat(expectedResponse).isEqualTo(responsePayload);
    }

    @Test
    void handleInvokesQueryHandlerReturningFaultyMessageStream() {
        // given...
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("faulty"), "hello", instanceOf(Integer.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then...
        assertThat(result.hasNextAvailable()).isFalse();
        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isPresent();
        assertThat(resultError.get()).isInstanceOf(QueryExecutionException.class);
    }

    @Test
    void handleInvokesQueryHandlerReturningAnEmptyOptional() {
        // given...
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("emptyOptional"),
                                                         "hello",
                                                         instanceOf(String.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then...
        assertThat(result.hasNextAvailable()).isFalse();
    }

    @Test
    void handleInvokesQueryHandlerReturningAnOptional() {
        // given...
        String expectedResponse = "hello";
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("optional"),
                                                         expectedResponse,
                                                         instanceOf(String.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then...
        assertThat(result.hasNextAvailable()).isTrue();
        Object responsePayload = result.first()
                                       .asCompletableFuture()
                                       .thenApply(MessageStream.Entry::message)
                                       .thenApply(Message::payload)
                                       .join();
        assertThat(expectedResponse).isEqualTo(responsePayload);
    }

    @Test
    void handleInvokesQueryHandlerReturningCollectionResultingInMessageStreamWithMultipleResponses() {
        // given...
        int desiredResponsesCount = 5;
        QueryMessage testQuery = new GenericQueryMessage(new MessageType("multipleResponses"),
                                                         desiredResponsesCount,
                                                         multipleInstancesOf(String.class));
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        List<Object> results = testSubject.handle(testQuery, testContext)
                                          .reduce(new ArrayList<>(), (list, entry) -> {
                                              list.add(entry.message().payload());
                                              return list;
                                          })
                                          .join();
        // then...
        assertThat(results.size()).isEqualTo(desiredResponsesCount);
    }

    private static class MyQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "echo")
        public String echo(String echo) {
            return echo;
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "faulty")
        public Integer faulty(String echo) {
            throw new MockException("Mock");
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "emptyOptional")
        public Optional<String> emptyOptional(String echo) {
            return Optional.empty();
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "optional")
        public Optional<String> optional(String echo) {
            return Optional.ofNullable(echo);
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "multipleResponses")
        public List<? extends String> multipleResponses(Integer count) {
            List<String> value = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                value.add("echo");
            }
            return value;
        }
    }

    private static class FaultyParameterOrderQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public String echo(Metadata metadata, String echo) {
            return echo;
        }
    }

    private static class VoidReturnTypeQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler
        public void echo(String echo) {
        }
    }
}
