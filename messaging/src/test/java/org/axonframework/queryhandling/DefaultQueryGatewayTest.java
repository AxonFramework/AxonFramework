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

import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class verifying correct workings of the {@link DefaultQueryGateway}.
 *
 * @author Allard Buijze
 */
class DefaultQueryGatewayTest {

    private static final MessageType QUERY_TYPE = new MessageType(String.class);
    private static final String QUERY_PAYLOAD = "query";
    private static final MessageType RESPONSE_TYPE = new MessageType(String.class);
    private static final String RESPONSE_PAYLOAD = "answer";

    private QueryBus queryBus;

    private DefaultQueryGateway testSubject;

    private ArgumentCaptor<QueryMessage> queryCaptor;
    private ArgumentCaptor<StreamingQueryMessage> streamingQueryCaptor;

    @BeforeEach
    void setUp() {
        queryBus = mock(QueryBus.class);

        testSubject = new DefaultQueryGateway(queryBus,
                                              new ClassBasedMessageTypeResolver(),
                                              QueryPriorityCalculator.defaultCalculator());

        queryCaptor = ArgumentCaptor.forClass(QueryMessage.class);
        streamingQueryCaptor = ArgumentCaptor.forClass(StreamingQueryMessage.class);
    }

    @Nested
    class QuerySingle {

        @Test
        void queryInvokesQueryBusWithSingleInstanceResponseType() throws Exception {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.just(testResponse));
            // when...
            CompletableFuture<String> result = testSubject.query(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result.get()).isEqualTo(RESPONSE_PAYLOAD);

            verify(queryBus).query(queryCaptor.capture(), eq(null));

            QueryMessage resultMessage = queryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(InstanceResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            assertThat(resultMessage.metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void queryWithMetadataInvokesQueryBusWithMetadata() throws Exception {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.just(testResponse));
            String expectedKey = "key";
            String expectedValue = "value";
            Metadata testMetadata = Metadata.with(expectedKey, expectedValue);
            Message testQuery = new GenericMessage(QUERY_TYPE, QUERY_PAYLOAD, testMetadata);
            // when...
            CompletableFuture<String> result = testSubject.query(testQuery, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result.get()).isEqualTo(RESPONSE_PAYLOAD);

            verify(queryBus).query(queryCaptor.capture(), eq(null));

            QueryMessage resultMessage = queryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(InstanceResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            Metadata resultMetadata = resultMessage.metadata();
            assertThat(resultMetadata).containsKey(expectedKey);
            assertThat(resultMetadata).containsValue(expectedValue);
        }

        @Test
        void queryReturningFailedMessageStreamReturnsExceptionalCompletableFuture() {
            // given...
            Throwable expected = new Throwable("oops");
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.failed(expected));
            // when...
            CompletableFuture<String> result = testSubject.query(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
            assertThat(result.exceptionNow().getMessage()).isEqualTo("oops");
        }

        @Test
        void cancellingResultFromQueryClosesMessageStreamFromQueryBus() {
            // given...
            MessageStream.Single<QueryResponseMessage> testResponseStream =
                    spy(MessageStream.fromFuture(new CompletableFuture<>()));
            when(queryBus.query(any(), eq(null))).thenReturn(testResponseStream);
            // when querying...
            CompletableFuture<String> result = testSubject.query(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isNotDone();
            // when canceling result...
            result.cancel(true);
            verify(testResponseStream).close();
        }

        @Test
        void queryReportsPayloadExtractionExceptions() {
            // given...
            when(queryBus.query(any(), eq(null)))
                    .thenReturn(MessageStream.just(new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD) {
                        @Override
                        public String payload() {
                            throw new MockException("Faking conversion problem");
                        }
                    }));
            // when...
            CompletableFuture<String> result = testSubject.query(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
            assertThat(result.exceptionNow().getMessage()).isEqualTo("Faking conversion problem");
        }
    }

    @Nested
    class QueryMany {

        @Test
        void queryManyInvokesQueryBusWithMultiInstanceResponseType() throws Exception {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.fromIterable(List.of(testResponse)));
            // when...
            CompletableFuture<List<String>> result = testSubject.queryMany(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            List<String> resultList = result.get();
            assertThat(resultList.size()).isEqualTo(1);
            assertThat(resultList.getFirst()).isEqualTo(RESPONSE_PAYLOAD);

            verify(queryBus).query(queryCaptor.capture(), eq(null));

            QueryMessage resultMessage = queryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(MultipleInstancesResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            assertThat(resultMessage.metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void queryManyWithMetadataInvokesQueryBusWithMetadata() throws Exception {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.fromIterable(List.of(testResponse)));
            String expectedKey = "key";
            String expectedValue = "value";
            Metadata testMetadata = Metadata.with(expectedKey, expectedValue);
            Message testQuery = new GenericMessage(QUERY_TYPE, QUERY_PAYLOAD, testMetadata);
            // when...
            CompletableFuture<List<String>> result = testSubject.queryMany(testQuery, String.class, null);
            // then...
            assertThat(result).isDone();
            List<String> resultList = result.get();
            assertThat(resultList.size()).isEqualTo(1);
            assertThat(resultList.getFirst()).isEqualTo(RESPONSE_PAYLOAD);

            verify(queryBus).query(queryCaptor.capture(), eq(null));

            QueryMessage resultMessage = queryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(MultipleInstancesResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            Metadata resultMetadata = resultMessage.metadata();
            assertThat(resultMetadata).containsKey(expectedKey);
            assertThat(resultMetadata).containsValue(expectedValue);
        }

        @Test
        void queryManyReturningFailedMessageStreamReturnsExceptionalCompletableFuture() {
            // given...
            Throwable expected = new Throwable("oops");
            when(queryBus.query(any(), eq(null))).thenReturn(MessageStream.failed(expected));
            // when...
            CompletableFuture<List<String>> result = testSubject.queryMany(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
            assertThat(result.exceptionNow().getMessage()).isEqualTo("oops");
        }

        @Test
        void cancellingResultFromQueryManyClosesMessageStreamFromQueryBus() {
            // given...
            MessageStream.Single<QueryResponseMessage> testResponseStream =
                    spy(MessageStream.fromFuture(new CompletableFuture<>()));
            when(queryBus.query(any(), eq(null))).thenReturn(testResponseStream);
            // when querying...
            CompletableFuture<List<String>> result = testSubject.queryMany(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isNotDone();
            // when canceling result...
            result.cancel(true);
            verify(testResponseStream).close();
        }

        @Test
        void queryManyReportsPayloadExtractionExceptions() {
            // given...
            when(queryBus.query(any(), eq(null)))
                    .thenReturn(MessageStream.fromIterable(List.of(
                            new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD) {
                                @Override
                                public String payload() {
                                    throw new MockException("Faking conversion problem");
                                }
                            }
                    )));
            // when...
            CompletableFuture<List<String>> result = testSubject.queryMany(QUERY_PAYLOAD, String.class, null);
            // then...
            assertThat(result).isDone();
            assertThat(result).isCompletedExceptionally();
            assertThat(result.exceptionNow().getMessage()).isEqualTo("Faking conversion problem");
        }
    }

    @Nested
    class StreamingQuery {

        @Test
        void streamingQueryInvokesQueryBusWithPublisherResponseType() {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.streamingQuery(any(), eq(null))).thenReturn(Mono.just(testResponse));
            // when...
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class, null))
                        .expectNext(RESPONSE_PAYLOAD)
                        .verifyComplete();
            // then...
            verify(queryBus).streamingQuery(streamingQueryCaptor.capture(), eq(null));

            StreamingQueryMessage resultMessage = streamingQueryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(PublisherResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            assertThat(resultMessage.metadata()).isEqualTo(Metadata.emptyInstance());
        }

        @Test
        void streamingQueryWithMetadataInvokesQueryBusWithMetadata() {
            // given...
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
            when(queryBus.streamingQuery(any(), eq(null))).thenReturn(Mono.just(testResponse));
            String expectedKey = "key";
            String expectedValue = "value";
            Metadata testMetadata = Metadata.with(expectedKey, expectedValue);
            Message testQuery = new GenericStreamingQueryMessage(QUERY_TYPE, QUERY_PAYLOAD, String.class)
                    .andMetadata(testMetadata);
            // when...
            StepVerifier.create(testSubject.streamingQuery(testQuery, String.class, null))
                        .expectNext(RESPONSE_PAYLOAD)
                        .verifyComplete();
            // then...
            verify(queryBus).streamingQuery(streamingQueryCaptor.capture(), eq(null));

            StreamingQueryMessage resultMessage = streamingQueryCaptor.getValue();
            assertThat(resultMessage.payload()).isEqualTo(QUERY_PAYLOAD);
            assertThat(resultMessage.payloadType()).isEqualTo(String.class);
            ResponseType<?> responseType = resultMessage.responseType();
            assertThat(responseType).isInstanceOf(PublisherResponseType.class);
            assertThat(responseType.getExpectedResponseType()).isEqualTo(String.class);
            Metadata resultMetadata = resultMessage.metadata();
            assertThat(resultMetadata).containsKey(expectedKey);
            assertThat(resultMetadata).containsValue(expectedValue);
        }

        @Test
        void streamingQueryIsLazy() {
            // given...
            Publisher<QueryResponseMessage> response = Flux.just(
                    new GenericQueryResponseMessage(QUERY_TYPE, "a"),
                    new GenericQueryResponseMessage(QUERY_TYPE, "b"),
                    new GenericQueryResponseMessage(QUERY_TYPE, "c")
            );
            when(queryBus.streamingQuery(any(), any())).thenReturn(response);
            // when first try without subscribing...
            //noinspection ReactiveStreamsUnusedPublisher
            testSubject.streamingQuery(QUERY_PAYLOAD, String.class, null);
            // then expect query never sent...
            verify(queryBus, never()).streamingQuery(any(), eq(null));

            // when second try with subscribing...
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class, null))
                        .expectNext("a", "b", "c")
                        .verifyComplete();
            // then expect query sent...
            verify(queryBus, times(1)).streamingQuery(any(StreamingQueryMessage.class), eq(null));
        }

        @Test
        void streamingQueryPropagateErrors() {
            // given...
            when(queryBus.streamingQuery(any(), any())).thenReturn(Flux.error(new IllegalStateException("test")));
            // when and then...
            StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class, null))
                        .expectErrorMatches(t -> t instanceof IllegalStateException && t.getMessage().equals("test"))
                        .verify();
        }
    }

    @Nested
    class SubscriptionQuery {


        @Test
        void subscriptionQuery() {
            when(queryBus.subscriptionQuery(any(), any(), anyInt()))
                    .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

            testSubject.subscriptionQuery("subscription", String.class, String.class, null);

            ArgumentCaptor<SubscriptionQueryMessage> queryMessageCaptor =
                    ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

            verify(queryBus).subscriptionQuery(queryMessageCaptor.capture(), eq(null), anyInt());

            SubscriptionQueryMessage result = queryMessageCaptor.getValue();
            assertEquals("subscription", result.payload());
            assertEquals(String.class, result.payloadType());
            assertTrue(InstanceResponseType.class.isAssignableFrom(result.responseType().getClass()));
            assertEquals(String.class, result.responseType().getExpectedResponseType());
            assertTrue(InstanceResponseType.class.isAssignableFrom(result.updatesResponseType().getClass()));
            assertEquals(String.class, result.updatesResponseType().getExpectedResponseType());
            assertEquals(Metadata.emptyInstance(), result.metadata());
        }

        @Test
        void subscriptionQueryWithMetadata() {
            String expectedMetadataKey = "key";
            String expectedMetadataValue = "value";

            when(queryBus.subscriptionQuery(any(), any(), anyInt()))
                    .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));


            Message testQuery = new GenericMessage(
                    QUERY_TYPE, "subscription",
                    Metadata.with(expectedMetadataKey, expectedMetadataValue)
            );
            testSubject.subscriptionQuery(testQuery, String.class, String.class, null);

            ArgumentCaptor<SubscriptionQueryMessage> queryMessageCaptor =
                    ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

            verify(queryBus).subscriptionQuery(queryMessageCaptor.capture(), eq(null), anyInt());

            SubscriptionQueryMessage result = queryMessageCaptor.getValue();
            assertEquals("subscription", result.payload());
            assertEquals(String.class, result.payloadType());
            assertTrue(InstanceResponseType.class.isAssignableFrom(result.responseType().getClass()));
            assertEquals(String.class, result.responseType().getExpectedResponseType());
            assertTrue(InstanceResponseType.class.isAssignableFrom(result.updatesResponseType().getClass()));
            assertEquals(String.class, result.updatesResponseType().getExpectedResponseType());
            Metadata resultMetadata = result.metadata();
            assertTrue(resultMetadata.containsKey(expectedMetadataKey));
            assertTrue(resultMetadata.containsValue(expectedMetadataValue));
        }

        @Test
        void exceptionInInitialResultOfSubscriptionQueryReportedInMono() {
            QueryResponseMessage testResponse = new GenericQueryResponseMessage(
                    QUERY_TYPE, new MockException(), String.class
            );
            when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), any(), anyInt()))
                    .thenReturn(new DefaultSubscriptionQueryResult<>(
                            Mono.just(testResponse),
                            Flux.empty(),
                            () -> true
                    ));

            SubscriptionQueryResult<String, String> actual =
                    testSubject.subscriptionQuery("Test", String.class, String.class, null);
            //noinspection NullableInLambdaInTransform
            assertEquals(
                    MockException.class,
                    actual.initialResult().map(i -> null).onErrorResume(e -> Mono.just(e.getClass())).block()
            );
        }

        @Test
        void nullInitialResultOfSubscriptionQueryReportedAsEmptyMono() {
            QueryResponseMessage testQuery = new GenericQueryResponseMessage(
                    QUERY_TYPE, (String) null, String.class
            );
            when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), any(), anyInt()))
                    .thenReturn(new DefaultSubscriptionQueryResult<>(
                            Mono.just(testQuery),
                            Flux.empty(),
                            () -> true
                    ));

            SubscriptionQueryResult<String, String> actual =
                    testSubject.subscriptionQuery("Test", String.class, String.class, null);

            assertNull(actual.initialResult().block());
        }

        @Test
        void nullUpdatesOfSubscriptionQuerySkipped() {
            SubscriptionQueryUpdateMessage testQuery = new GenericSubscriptionQueryUpdateMessage(
                    QUERY_TYPE, null, String.class
            );
            when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), eq(null), anyInt()))
                    .thenReturn(new DefaultSubscriptionQueryResult<>(
                            Mono.empty(),
                            Flux.just(testQuery),
                            () -> true
                    ));

            SubscriptionQueryResult<String, String> actual =
                    testSubject.subscriptionQuery("Test", String.class, String.class, null);

            assertNull(actual.initialResult().block());
            assertEquals((Long) 0L, actual.updates().count().block());
        }

        @SuppressWarnings({"SameParameterValue", "unused"})
        private <Q, R> SubscriptionQueryMessage anySubscriptionMessage(Class<Q> queryType, Class<R> responseType) {
            return any();
        }
    }
}
