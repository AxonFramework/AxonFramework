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
package org.axonframework.queryhandling;

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryBus}.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
class SimpleQueryBusTest {

    private static final QualifiedName QUERY_NAME = new QualifiedName("query");
    private static final MessageType QUERY_TYPE = new MessageType(QUERY_NAME);
    private static final String QUERY_PAYLOAD = "query";
    private static final QualifiedName RESPONSE_NAME = new QualifiedName(String.class);
    private static final MessageType RESPONSE_TYPE = new MessageType(RESPONSE_NAME);
    private static final QualifiedName UPDATE_NAME = new QualifiedName("update");
    private static final MessageType UPDATE_TYPE = new MessageType(UPDATE_NAME);
    private static final String UPDATE_PAYLOAD = "Update";

    private static final QueryHandler SINGLE_RESPONSE_HANDLER = (query, context) -> {
        QueryResponseMessage response = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
        return MessageStream.just(response);
    };
    private static final QueryHandler MULTI_RESPONSE_HANDLER = (query, context) -> {
        QueryResponseMessage responseOne = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
        QueryResponseMessage responseTwo = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "5678");
        return MessageStream.fromIterable(List.of(responseOne, responseTwo));
    };
    private static final ResponseType<String> SINGLE_STRING_RESPONSE = instanceOf(String.class);
    private static final ResponseType<List<String>> MULTI_STRING_RESPONSE = multipleInstancesOf(String.class);
    private static final ResponseType<Long> LONG_STRING_RESPONSE = instanceOf(Long.class);

    private SimpleQueryBus testSubject;

    private TransactionManager transactionManager;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        transactionManager = mock(TransactionManager.class);
        testTransaction = mock(Transaction.class);
        when(transactionManager.startTransaction()).thenReturn(testTransaction);
        UnitOfWorkFactory unitOfWorkFactory =
                new TransactionalUnitOfWorkFactory(transactionManager, UnitOfWorkTestUtils.SIMPLE_FACTORY);

        testSubject = new SimpleQueryBus(unitOfWorkFactory);
    }

    @Nested
    class HandlerSubscriptions {

        @Test
        void subscribeAddsQueryHandlers() {
            // given...
            MockComponentDescriptor testDescriptor = new MockComponentDescriptor();
            // when first subscription...
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            Map<QueryHandlerName, QueryHandler> subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(1);
            assertThat(subscriptions).containsValue(SINGLE_RESPONSE_HANDLER);
            // when second subscription with different query  name...
            testSubject.subscribe(new QualifiedName("test2"), RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(2);
            assertThat(subscriptions).containsValue(SINGLE_RESPONSE_HANDLER);
            // when third subscription with different response name...
            testSubject.subscribe(QUERY_NAME, new QualifiedName(Integer.class), SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(3);
        }

        @Test
        void subscribingSameHandlerTwiceDoesNotThrowDuplicateQueryHandlerSubscriptionException() {
            // given...
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when/then...
            assertDoesNotThrow(() -> testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER));
        }

        @Test
        void subscribingDifferentHandlerForSameNamesThrowsDuplicateQueryHandlerSubscriptionException() {
            // given...
            QueryHandler testHandler = (query, context) -> MessageStream.empty().cast();
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when/then...
            assertThatThrownBy(() -> testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, testHandler))
                    .isInstanceOf(DuplicateQueryHandlerSubscriptionException.class);
        }
    }

    @Nested
    class DirectQuery {

        @Test
        void directQueryForUnknownQueryNameAndResponseNameReturnsFailedNoHandlerForQueryExceptionStream() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            assertThat(result.isCompleted()).isTrue();
            Optional<Throwable> optionalError = result.error();
            assertThat(optionalError).isPresent();
            assertThat(optionalError.get()).isInstanceOf(NoHandlerForQueryException.class);
        }

        @Test
        void directQueryReturnsMessageStreamWithSingleEntry() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            assertThat(result.isCompleted()).isFalse();
            assertThat(result.hasNextAvailable()).isTrue();
            Optional<MessageStream.Entry<QueryResponseMessage>> nextResponse = result.next();
            assertThat(nextResponse).isPresent();
            assertThat(nextResponse.get().message().payload()).isEqualTo("query1234");
            assertThat(result.isCompleted()).isTrue();
            assertThat(result.hasNextAvailable()).isFalse();
        }

        @Test
        void directQueryReturnsFailedMessageStreamFromThrowingQueryHandler() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            QueryHandler failingHandler = (query, context) -> {
                throw new MockException("Mock");
            };
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, failingHandler);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            assertThat(result.isCompleted()).isTrue();
            assertThat(result.hasNextAvailable()).isFalse();
            Optional<Throwable> optionalError = result.error();
            assertThat(optionalError).isPresent();
            assertThat(optionalError.get()).isInstanceOf(MockException.class);
            assertThat(optionalError.get().getMessage()).isEqualTo("Mock");
        }

        @Test
        void directQueryReturnsFailedMessageStreamFromFailingStreamResultQueryHandler() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            QueryHandler failingHandler = (query, context) -> MessageStream.failed(new MockException("Mock"));
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, failingHandler);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            assertThat(result.isCompleted()).isTrue();
            assertThat(result.hasNextAvailable()).isFalse();
            Optional<Throwable> optionalError = result.error();
            assertThat(optionalError).isPresent();
            assertThat(optionalError.get()).isInstanceOf(MockException.class);
            assertThat(optionalError.get().getMessage()).isEqualTo("Mock");
        }

        @Test
        void directQueryResultsInEmptyMessageStream() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> MessageStream.empty().cast());
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            assertThat(result.isCompleted()).isTrue();
            assertThat(result.error()).isNotPresent();
            assertThat(result.hasNextAvailable()).isFalse();
        }

        @Test
        void directQueryForMultiResponsesWithSingleResponseHandlerReturnsSingleResponse() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, MULTI_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            Optional<MessageStream.Entry<QueryResponseMessage>> nextResponse = result.next();
            assertThat(nextResponse).isPresent();
            assertThat(nextResponse.get().message().payload()).isEqualTo("query1234");
            assertThat(result.isCompleted()).isTrue();
        }

        @Test
        void directQueryForMultiResponsesWithMultiResponseHandlerReturnsMultipleResponses() {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, MULTI_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, MULTI_RESPONSE_HANDLER);
            // when...
            MessageStream<QueryResponseMessage> result = testSubject.query(testQuery, null);
            // then...
            Optional<MessageStream.Entry<QueryResponseMessage>> nextResponse = result.next();
            assertThat(nextResponse).isPresent();
            assertThat(nextResponse.get().message().payload()).isEqualTo("query1234");
            nextResponse = result.next();
            assertThat(nextResponse).isPresent();
            assertThat(nextResponse.get().message().payload()).isEqualTo("query5678");
            assertThat(result.isCompleted()).isTrue();
        }

        @Test
        void directQuerySingleWithTransaction() throws Exception {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when...
            CompletableFuture<Object> result = testSubject.query(testQuery, null)
                                                          .first()
                                                          .asCompletableFuture()
                                                          .thenApply(entry -> entry.message().payload());
            // then...
            assertEquals("query1234", result.get());
            verify(transactionManager).startTransaction();
            verify(testTransaction).commit();
        }

        @Test
        void directQueryMultipleWithTransaction() throws Exception {
            // given...
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, MULTI_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, MULTI_RESPONSE_HANDLER);
            // when...
            CompletableFuture<List<String>> result =
                    testSubject.query(testQuery, null)
                               .reduce(new ArrayList<>(), (results, entry) -> {
                                   results.add(entry.message().payloadAs(String.class));
                                   return results;
                               });
            // then...
            assertTrue(result.isDone());
            List<String> completedResult = result.get();
            assertTrue(completedResult.contains("query1234"));
            assertTrue(completedResult.contains("query5678"));
            verify(transactionManager).startTransaction();
            verify(testTransaction).commit();
        }
    }

    @Nested
    class StreamingQuery {

        @Test
        void streamingQueryIsLazy() {
            // given...
            AtomicBoolean invoked = new AtomicBoolean(false);
            StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> {
                invoked.set(true);
                QueryResponseMessage response =
                        new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
                return MessageStream.just(response);
            });
            // when...
            Publisher<QueryResponseMessage> result = testSubject.streamingQuery(testQuery, null);
            // then...
            assertThat(invoked).isFalse();
            StepVerifier.create(result)
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query1234"))
                        .verifyComplete();
            assertThat(invoked).isTrue();
        }

        @Test
        void streamingQueryForUnknownQueryNameAndResponseNameReturnsFailedNoHandlerForQueryExceptionPublisherStream() {
            // given...
            StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class);
            // when/then...
            StepVerifier.create(testSubject.streamingQuery(testQuery, null))
                        .expectError(NoHandlerForQueryException.class)
                        .verify();
        }

        @Test
        void streamingQueryReturnsPublisherWithSingleEntry() {
            // given...
            StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when/then...
            StepVerifier.create(testSubject.streamingQuery(testQuery, null))
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query1234"))
                        .verifyComplete();
        }

        @Test
        void streamingQueryReturnsFailedPublisherFromFailingStreamResultQueryHandler() {
            // given...
            StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class);
            QueryHandler failingHandler = (query, context) -> MessageStream.failed(new MockException("Mock"));
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, failingHandler);
            // when/then...
            StepVerifier.create(testSubject.streamingQuery(testQuery, null))
                        .verifyErrorMatches(throwable -> throwable instanceof MockException mockException
                                && mockException.getMessage().equals("Mock"));
        }

        @Test
        void streamingQueryResultsInEmptyMessageStream() {
            // given...
            StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> MessageStream.empty().cast());
            // when/then...
            StepVerifier.create(testSubject.streamingQuery(testQuery, null))
                        .verifyComplete();
        }
    }

    /**
     * Nested test class validating the {@link SubscriptionQueryResponseMessages#initialResult()} of the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)}.
     */
    @Nested
    class SubscriptionQuery {

        @Test
        void subscriptionQueryInitialResultIsLazy() {
            // given...
            AtomicBoolean invoked = new AtomicBoolean(false);
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> {
                invoked.set(true);
                QueryResponseMessage response =
                        new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
                return MessageStream.just(response);
            });
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            Flux<QueryResponseMessage> initialResult = result.initialResult();
            // then...
            assertThat(invoked).isFalse();
            StepVerifier.create(initialResult)
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query1234"))
                        .verifyComplete();
            assertThat(invoked).isTrue();
        }

        @Test
        void subscriptionQueryForUnknownQueryNameAndResponseNameReturnsFailedNoHandlerForQueryExceptionInitialResult() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // then...
            StepVerifier.create(result.initialResult())
                        .expectError(NoHandlerForQueryException.class)
                        .verify();
        }

        @Test
        void subscriptionQueryInitialResultReturnsSingleEntry() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // then...
            StepVerifier.create(result.initialResult())
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query1234"))
                        .verifyComplete();
        }

        @Test
        void subscriptionQueryInitialResultReturnsMultipleEntries() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, MULTI_RESPONSE_HANDLER);
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // then...
            StepVerifier.create(result.initialResult())
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query1234"))
                        .expectNextMatches(response -> Objects.equals(response.payload(), "query5678"))
                        .verifyComplete();
        }

        @Test
        void subscriptionQueryInitialResultReturnsEmptyMessageStream() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> MessageStream.empty().cast());
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // when/then...
            StepVerifier.create(result.initialResult())
                        .verifyComplete();
        }

        @Test
        void subscriptionQueryInitialResultReportsException() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            QueryHandler failingHandler = (query, context) -> {
                throw new MockException("Mock");
            };
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, failingHandler);
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // then...
            StepVerifier.create(result.initialResult())
                        .expectError(MockException.class)
                        .verify();
        }

        @Test
        void subscriptionQueryDoesNotAllowPublisherAsInitialOrUpdateResponseType() {
            // given...
            SubscriptionQueryMessage initialPublisherType = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, ResponseTypes.publisherOf(Flux.class), SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage updatePublisherType = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, ResponseTypes.publisherOf(Flux.class)
            );
            // when/then...
            assertThatThrownBy(() -> testSubject.subscriptionQuery(initialPublisherType,
                                                                   null,
                                                                   Queues.SMALL_BUFFER_SIZE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> testSubject.subscriptionQuery(updatePublisherType, null, Queues.SMALL_BUFFER_SIZE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void subscriptionQueryIncreasingProjection() throws InterruptedException {
            // given...
            CountDownLatch ten = new CountDownLatch(1);
            CountDownLatch hundred = new CountDownLatch(1);
            CountDownLatch thousand = new CountDownLatch(1);
            final AtomicLong value = new AtomicLong();
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, LONG_STRING_RESPONSE, LONG_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter =
                    query -> testQuery.identifier().equals(query.identifier());
            testSubject.subscribe(QUERY_NAME, new QualifiedName(Long.class), (query, context) -> {
                QueryResponseMessage response = new GenericQueryResponseMessage(RESPONSE_TYPE, value.get());
                return MessageStream.just(response);
            });
            Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(3))
                                        .doOnNext(next -> {
                                            if (next == 10L) {
                                                ten.countDown();
                                            }
                                            if (next == 100L) {
                                                hundred.countDown();
                                            }
                                            if (next == 1000L) {
                                                thousand.countDown();
                                            }
                                            value.set(next);
                                            SubscriptionQueryUpdateMessage update =
                                                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, next);
                                            testSubject.emitUpdate(queryFilter, () -> update, null);
                                        })
                                        .doOnComplete(() -> testSubject.completeSubscriptions(queryFilter, null))
                                        .subscribe();
            // when...
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // then...
            Flux<QueryResponseMessage> initialResult = result.initialResult();
            ten.await();
            Long firstInitialResult = Objects.requireNonNull(initialResult.next().block()).payloadAs(Long.class);
            assertThat(firstInitialResult).isNotNull();
            hundred.await();
            Long fistUpdate = Objects.requireNonNull(result.updates().next().block()).payloadAs(Long.class);
            thousand.await();
            Long anotherInitialResult = Objects.requireNonNull(initialResult.next().block()).payloadAs(Long.class);
            assertThat(fistUpdate).isLessThan(firstInitialResult + 1);
            assertThat(firstInitialResult).isLessThan(anotherInitialResult);
            disposable.dispose();
        }
    }

    @Nested
    class SubscribeToUpdates {

        @Test
        void subscribeToUpdatesForSameQueryTwiceThrowsSubscriptionQueryAlreadyRegisteredException() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            testSubject.subscribeToUpdates(testQuery, Queues.SMALL_BUFFER_SIZE);
            // when/then...
            assertThatThrownBy(() -> testSubject.subscribeToUpdates(testQuery, Queues.SMALL_BUFFER_SIZE))
                    .isInstanceOf(SubscriptionQueryAlreadyRegisteredException.class);
        }

        @Test
        void completingUpdateHandlerFromSubscribeToUpdatesCompletesUpdates() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            UpdateHandler result = testSubject.subscribeToUpdates(testQuery, Queues.SMALL_BUFFER_SIZE);
            testSubject.emitUpdate(query -> true, () -> updateMessage, null).join();
            // when...
            result.complete();
            // then...
            StepVerifier.create(result.updates().mapNotNull(Message::payload))
                        .expectNext(UPDATE_PAYLOAD)
                        .verifyComplete();
        }

        @Test
        void cancelingUpdateHandlerFromSubscribeToUpdatesDoesNotCompleteButTimesOutUpdates() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            UpdateHandler result = testSubject.subscribeToUpdates(testQuery, Queues.SMALL_BUFFER_SIZE);
            testSubject.emitUpdate(query -> true, () -> updateMessage, null).join();
            // when...
            result.cancel();
            // then...
            StepVerifier.create(result.updates().mapNotNull(Message::payload))
                        .expectNext(UPDATE_PAYLOAD)
                        .verifyTimeout(Duration.ofMillis(500));
        }
    }

    /**
     * Nested test class validating
     * {@link QueryBus#emitUpdate(Predicate, java.util.function.Supplier, ProcessingContext)} are handled by the
     * {@link SubscriptionQueryResponseMessages#updates()} as expected.
     */
    @Nested
    class EmitUpdate {

        @Test
        void emittedUpdateLandsInSubscriptionQueryUpdates() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter =
                    query -> query.identifier().equals(testQuery.identifier());
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            SubscriptionQueryResponseMessages responses =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // when...
            testSubject.emitUpdate(queryFilter, () -> updateMessage, null).join();
            // then...
            StepVerifier.create(responses.updates())
                        .expectNextMatches(response -> Objects.equals(response.payload(), UPDATE_PAYLOAD))
                        .verifyTimeout(Duration.ofMillis(100));
        }

        @Test
        void emittedUpdateLandsInAllMatchingSubscriptionQueryUpdates() {
            // given...
            SubscriptionQueryMessage testQueryOne = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryTwo = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryThree = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            // Filter allows emitting updates to subscription queries one and two
            Predicate<SubscriptionQueryMessage> queryFilter = query ->
                    query.identifier().equals(testQueryOne.identifier())
                            || query.identifier().equals(testQueryTwo.identifier());
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            SubscriptionQueryResponseMessages firstResponses =
                    testSubject.subscriptionQuery(testQueryOne, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages secondResponses =
                    testSubject.subscriptionQuery(testQueryTwo, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages thirdResponses =
                    testSubject.subscriptionQuery(testQueryThree, null, Queues.SMALL_BUFFER_SIZE);
            // when...
            testSubject.emitUpdate(queryFilter, () -> updateMessage, null).join();
            // then...
            StepVerifier.create(firstResponses.updates())
                        .expectNextMatches(response -> Objects.equals(response.payload(), UPDATE_PAYLOAD))
                        .verifyTimeout(Duration.ofMillis(100));
            StepVerifier.create(secondResponses.updates())
                        .expectNextMatches(response -> Objects.equals(response.payload(), UPDATE_PAYLOAD))
                        .verifyTimeout(Duration.ofMillis(100));
            StepVerifier.create(thirdResponses.updates())
                        .verifyTimeout(Duration.ofMillis(100));
        }

        @Test
        void emittingUpdatesConcurrently() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            UpdateHandler updateHandler = testSubject.subscribeToUpdates(testQuery, 128);
            // when...
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 100; i++) {
                    executor.submit(() -> testSubject.emitUpdate(q -> true, () -> updateMessage, null));
                }
                executor.shutdown();
            }
            // then...
            Flux<SubscriptionQueryUpdateMessage> updates = updateHandler.updates();
            StepVerifier.create(updates)
                        .expectNextCount(100)
                        .then(() -> testSubject.completeSubscriptions(query -> true, null))
                        .verifyComplete();
        }

        @Test
        void emittingUpdatesWithProcessingContextStagesEmitsToAfterCommit() {
            // given...
            AtomicBoolean filterInvoked = new AtomicBoolean(false);
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter =
                    query -> {
                        filterInvoked.set(true);
                        return query.identifier().equals(testQuery.identifier());
                    };
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            // We should have a subscription query for an update handler to even exist.
            SubscriptionQueryResponseMessages result =
                    testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            UnitOfWork uow = UnitOfWorkTestUtils.aUnitOfWork();
            // when emitting on invocation...
            uow.onInvocation(context -> testSubject.emitUpdate(queryFilter, () -> updateMessage, context));
            // then before the after commit phase validate the filter was not invoked yet...
            List<String> updateList = new ArrayList<>();
            result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(updateList::add);
            assertThat(filterInvoked).isFalse();
            assertThat(updateList).isEmpty();
            // when executing the UnitOfWork, we pass the after commit phase
            uow.execute().join();
            // then we expect the update to be emitted, validated by the filter being invoked...
            assertThat(filterInvoked).isTrue();
            assertThat(updateList).isNotEmpty();
            assertThat(updateList).containsExactlyInAnyOrder(UPDATE_PAYLOAD);
        }

        @Test
        void emittingUpdatesDoesNotRetrieveUpdateWhenNoQueriesMatch() {
            // given...
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter = query -> false;
            AtomicBoolean updateSupplierInvoked = new AtomicBoolean(false);
            Supplier<SubscriptionQueryUpdateMessage> updateSupplier = () -> {
                updateSupplierInvoked.set(true);
                return null;
            };
            // We should have a subscription query for an update handler to even exist.
            testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            // when...
            testSubject.emitUpdate(queryFilter, updateSupplier, null).join();
            // then...
            assertThat(updateSupplierInvoked).isFalse();
        }
    }

    @Nested
    class CompleteSubscription {

        @Test
        void completingSubscriptionCompletesMatchingSubscriptionQueriesOnly() {
            // given...
            SubscriptionQueryMessage testQueryOne = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryTwo = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryThree = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            // Filter will complete subscription queries one and two
            Predicate<SubscriptionQueryMessage> queryFilter = query ->
                    query.identifier().equals(testQueryOne.identifier())
                            || query.identifier().equals(testQueryTwo.identifier());
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            SubscriptionQueryResponseMessages firstResponses =
                    testSubject.subscriptionQuery(testQueryOne, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages secondResponses =
                    testSubject.subscriptionQuery(testQueryTwo, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages thirdResponses =
                    testSubject.subscriptionQuery(testQueryThree, null, Queues.SMALL_BUFFER_SIZE);
            // when...
            testSubject.completeSubscriptions(queryFilter, null).join();
            // then...
            testSubject.emitUpdate(query -> true, () -> updateMessage, null).join();
            StepVerifier.create(firstResponses.updates())
                        .verifyComplete();
            StepVerifier.create(secondResponses.updates())
                        .verifyComplete();
            StepVerifier.create(thirdResponses.updates())
                        .expectNextMatches(response -> Objects.equals(response.payload(), UPDATE_PAYLOAD))
                        .verifyTimeout(Duration.ofMillis(100));
        }

        @Test
        void completingSubscriptionsWithProcessingContextStagesCompletionToAfterCommit() {
            // given...
            AtomicBoolean filterInvoked = new AtomicBoolean(false);
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter =
                    query -> {
                        filterInvoked.set(true);
                        return query.identifier().equals(testQuery.identifier());
                    };
            // We should have a subscription query for an update handler to even exist.
            testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            UnitOfWork uow = UnitOfWorkTestUtils.aUnitOfWork();
            // when emitting on invocation...
            uow.onInvocation(context -> testSubject.completeSubscriptions(queryFilter, context));
            // then before the after commit phase validate the filter was not invoked yet...
            assertThat(filterInvoked).isFalse();
            // when executing the UnitOfWork, we pass the after commit phase
            uow.execute().join();
            // then we expect the update to be emitted, validated by the filter being invoked...
            assertThat(filterInvoked).isTrue();
        }
    }

    @Nested
    class CompleteSubscriptionExceptionally {

        @Test
        void completingSubscriptionExceptionallyCompletesMatchingSubscriptionQueriesOnly() {
            // given...
            SubscriptionQueryMessage testQueryOne = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryTwo = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            SubscriptionQueryMessage testQueryThree = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            // Filter will complete subscription queries one and two
            Predicate<SubscriptionQueryMessage> queryFilter = query ->
                    query.identifier().equals(testQueryOne.identifier())
                            || query.identifier().equals(testQueryTwo.identifier());
            MockException mockException = new MockException("Mock");
            SubscriptionQueryUpdateMessage updateMessage =
                    new GenericSubscriptionQueryUpdateMessage(UPDATE_TYPE, UPDATE_PAYLOAD);
            SubscriptionQueryResponseMessages firstResponses =
                    testSubject.subscriptionQuery(testQueryOne, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages secondResponses =
                    testSubject.subscriptionQuery(testQueryTwo, null, Queues.SMALL_BUFFER_SIZE);
            SubscriptionQueryResponseMessages thirdResponses =
                    testSubject.subscriptionQuery(testQueryThree, null, Queues.SMALL_BUFFER_SIZE);
            // when...
            testSubject.completeSubscriptionsExceptionally(queryFilter, mockException, null).join();
            // then...
            testSubject.emitUpdate(query -> true, () -> updateMessage, null).join();
            StepVerifier.create(firstResponses.updates())
                        .verifyError(MockException.class);
            StepVerifier.create(secondResponses.updates())
                        .verifyError(MockException.class);
            StepVerifier.create(thirdResponses.updates())
                        .expectNextMatches(response -> Objects.equals(response.payload(), UPDATE_PAYLOAD))
                        .verifyTimeout(Duration.ofMillis(100));
        }

        @Test
        void completingSubscriptionsExceptionallyWithProcessingContextStagesCompletionToAfterCommit() {
            // given...
            AtomicBoolean filterInvoked = new AtomicBoolean(false);
            SubscriptionQueryMessage testQuery = new GenericSubscriptionQueryMessage(
                    QUERY_TYPE, QUERY_PAYLOAD, SINGLE_STRING_RESPONSE, SINGLE_STRING_RESPONSE
            );
            Predicate<SubscriptionQueryMessage> queryFilter =
                    query -> {
                        filterInvoked.set(true);
                        return query.identifier().equals(testQuery.identifier());
                    };
            MockException mockException = new MockException("Mock");
            // We should have a subscription query for an update handler to even exist.
            testSubject.subscriptionQuery(testQuery, null, Queues.SMALL_BUFFER_SIZE);
            UnitOfWork uow = UnitOfWorkTestUtils.aUnitOfWork();
            // when emitting on invocation...
            uow.onInvocation(context -> testSubject.completeSubscriptionsExceptionally(
                    queryFilter, mockException, context
            ));
            // then before the after commit phase validate the filter was not invoked yet...
            assertThat(filterInvoked).isFalse();
            // when executing the UnitOfWork, we pass the after commit phase
            uow.execute().join();
            // then we expect the update to be emitted, validated by the filter being invoked...
            assertThat(filterInvoked).isTrue();
        }
    }
}
