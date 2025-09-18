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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final QualifiedName RESPONSE_NAME = new QualifiedName(String.class);
    private static final MessageType RESPONSE_TYPE = new MessageType(RESPONSE_NAME);
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

        testSubject = new SimpleQueryBus(unitOfWorkFactory, SimpleQueryUpdateEmitter.builder().build());
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", MULTI_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", MULTI_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
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
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", MULTI_STRING_RESPONSE);
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

    @Test
    @Disabled("TODO #3488 - Pick up together with subscription query implementation")
    void subscriptionQueryReportsExceptionInInitialResult() {
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> {
//            throw new MockException();
//        });

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType(String.class), "test", instanceOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);
        Mono<QueryResponseMessage> initialResult = result.initialResult();
        //noinspection ConstantConditions
        assertFalse(initialResult.map(r -> false).onErrorReturn(MockException.class::isInstance, true).block(),
                    "Exception by handler should be reported in result, not on Mono");
        //noinspection ConstantConditions
        assertTrue(initialResult.block().isExceptional());
    }

    @Disabled("TODO together with #3079")
    @Test
    void subscriptionQueryIncreasingProjection() throws InterruptedException {
        CountDownLatch ten = new CountDownLatch(1);
        CountDownLatch hundred = new CountDownLatch(1);
        CountDownLatch thousand = new CountDownLatch(1);
        final AtomicLong value = new AtomicLong();
//        testSubject.subscribe("queryName", Long.class, (q, ctx) -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
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
                                        updateEmitter.emit(query -> "queryName".equals(query.type().name()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.type()
                                                                                                                .name())))
                                    .subscribe();


        SubscriptionQueryMessage<String, Long, Long> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("queryName"), "test",
                instanceOf(Long.class), instanceOf(Long.class)
        );
        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);
        Mono<QueryResponseMessage> initialResult = result.initialResult();
        ten.await();
        Long firstInitialResult = Objects.requireNonNull(initialResult.block()).payloadAs(Long.class);
        hundred.await();
        Long fistUpdate = Objects.requireNonNull(result.updates().next().block()).payloadAs(Long.class);
        thousand.await();
        Long anotherInitialResult = Objects.requireNonNull(initialResult.block()).payloadAs(Long.class);
        assertTrue(fistUpdate <= firstInitialResult + 1);
        assertTrue(firstInitialResult <= anotherInitialResult);
        disposable.dispose();
    }

    @Test
    @Disabled("TODO #3488 - Pick up together with subscription query implementation")
    void onSubscriptionQueryCancelTheActiveSubscriptionIsRemovedFromTheEmitterIfFluxIsNotSubscribed() {
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType(String.class), "test", instanceOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);

        result.cancel();
        assertEquals(0, testSubject.queryUpdateEmitter().activeSubscriptions().size());
    }
}
