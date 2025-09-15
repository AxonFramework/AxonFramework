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
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
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

    private ArgumentCaptor<QueryMessage> messageCaptor;
    private QueryResponseMessage answer;

    @BeforeEach
    void setUp() {
        queryBus = mock(QueryBus.class);

        testSubject = new DefaultQueryGateway(queryBus, new ClassBasedMessageTypeResolver(), null);

        messageCaptor = ArgumentCaptor.forClass(QueryMessage.class);
        answer = new GenericQueryResponseMessage(RESPONSE_TYPE, RESPONSE_PAYLOAD);
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

            verify(queryBus).query(messageCaptor.capture(), eq(null));

            QueryMessage resultMessage = messageCaptor.getValue();
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

            verify(queryBus).query(messageCaptor.capture(), eq(null));

            QueryMessage resultMessage = messageCaptor.getValue();
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
    }

    @Test
    void pointToPointQueryWhenQueryBusThrowsException() throws Exception {
        Throwable expected = new Throwable("oops");
        CompletableFuture<QueryResponseMessage> queryResponseCompletableFuture = new CompletableFuture<>();
        queryResponseCompletableFuture.completeExceptionally(expected);
        // TODO fix as part of gateway fix
//        when(queryBus.query(anyMessage(String.class, String.class))).thenReturn(queryResponseCompletableFuture);

        CompletableFuture<String> result = testSubject.query("query", String.class);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void scatterGatherQuery() {
        long expectedTimeout = 1L;
        TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

        when(queryBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<String> queryResponse =
                testSubject.scatterGather("scatterGather", instanceOf(String.class), expectedTimeout, expectedTimeUnit);
        Optional<String> firstResult = queryResponse.findFirst();
        assertTrue(firstResult.isPresent());
        assertEquals(RESPONSE_PAYLOAD, firstResult.get());

        ArgumentCaptor<QueryMessage> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(queryBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

        QueryMessage result = queryMessageCaptor.getValue();
        assertEquals("scatterGather", result.payload());
        assertEquals(String.class, result.payloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.responseType().getClass()));
        assertEquals(String.class, result.responseType().getExpectedResponseType());
        assertEquals(Metadata.emptyInstance(), result.metadata());
    }

    @Test
    void scatterGatherQueryWithMetadata() {
        String expectedMetadataKey = "key";
        String expectedMetadataValue = "value";
        long expectedTimeout = 1L;
        TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

        when(queryBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Message testQuery = new GenericMessage(
                QUERY_TYPE, "scatterGather",
                Metadata.with(expectedMetadataKey, expectedMetadataValue)
        );

        Stream<String> queryResponse =
                testSubject.scatterGather(testQuery, instanceOf(String.class), expectedTimeout, expectedTimeUnit);
        Optional<String> firstResult = queryResponse.findFirst();
        assertTrue(firstResult.isPresent());
        assertEquals(RESPONSE_PAYLOAD, firstResult.get());

        ArgumentCaptor<QueryMessage> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(queryBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

        QueryMessage result = queryMessageCaptor.getValue();
        assertEquals("scatterGather", result.payload());
        assertEquals(String.class, result.payloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.responseType().getClass()));
        assertEquals(String.class, result.responseType().getExpectedResponseType());
        Metadata resultMetadata = result.metadata();
        assertTrue(resultMetadata.containsKey(expectedMetadataKey));
        assertTrue(resultMetadata.containsValue(expectedMetadataValue));
    }

    @Test
    void subscriptionQuery() {
        when(queryBus.subscriptionQuery(any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

        testSubject.subscriptionQuery("subscription", instanceOf(String.class), instanceOf(String.class));

        //noinspection unchecked
        ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor =
                ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

        verify(queryBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

        SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
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

        when(queryBus.subscriptionQuery(any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));


        Message testQuery = new GenericMessage(
                QUERY_TYPE, "subscription",
                Metadata.with(expectedMetadataKey, expectedMetadataValue)
        );
        testSubject.subscriptionQuery(testQuery, instanceOf(String.class), instanceOf(String.class));

        //noinspection unchecked
        ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor =
                ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

        verify(queryBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

        SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
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
        when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(
                        Mono.just(testResponse),
                        Flux.empty(),
                        () -> true
                ));

        SubscriptionQueryResult<String, String> actual =
                testSubject.subscriptionQuery("Test", instanceOf(String.class), instanceOf(String.class));
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
        when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(
                        Mono.just(testQuery),
                        Flux.empty(),
                        () -> true
                ));

        SubscriptionQueryResult<String, String> actual =
                testSubject.subscriptionQuery("Test", instanceOf(String.class), instanceOf(String.class));

        assertNull(actual.initialResult().block());
    }

    @Test
    void nullUpdatesOfSubscriptionQuerySkipped() {
        SubscriptionQueryUpdateMessage testQuery = new GenericSubscriptionQueryUpdateMessage(
                QUERY_TYPE, null, String.class
        );
        when(queryBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(
                        Mono.empty(),
                        Flux.just(testQuery),
                        () -> true
                ));

        SubscriptionQueryResult<String, String> actual =
                testSubject.subscriptionQuery("Test", instanceOf(String.class), instanceOf(String.class));

        assertNull(actual.initialResult().block());
        assertEquals((Long) 0L, actual.updates().count().block());
    }

    @Test
    void payloadExtractionProblemsReportedInException() throws ExecutionException, InterruptedException {
        // TODO fix as part of gateway fix
//        when(queryBus.query(anyMessage(String.class, String.class)))
//                .thenReturn(completedFuture(new GenericQueryResponseMessage(
//                        new MessageType("query"), "test"
//                ) {
//                    @Override
//                    public String payload() {
//                        throw new MockException("Faking serialization problem");
//                    }
//                }));

        CompletableFuture<String> actual = testSubject.query(QUERY_PAYLOAD, String.class, null);
        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());
        assertEquals("Faking serialization problem", actual.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void streamingQueryIsLazy() {
        Publisher<QueryResponseMessage> response = Flux.just(
                new GenericQueryResponseMessage(QUERY_TYPE, "a"),
                new GenericQueryResponseMessage(QUERY_TYPE, "b"),
                new GenericQueryResponseMessage(QUERY_TYPE, "c")
        );

        when(queryBus.streamingQuery(any()))
                .thenReturn(response);

        //first try without subscribing
        testSubject.streamingQuery(QUERY_PAYLOAD, String.class);

        //expect query never sent
        verify(queryBus, never()).streamingQuery(any());

        //second try with subscribing
        StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class))
                    .expectNext("a", "b", "c")
                    .verifyComplete();

        //expect query sent
        verify(queryBus, times(1)).streamingQuery(any(StreamingQueryMessage.class));
    }

    @Test
    void streamingQueryPropagateErrors() {
        when(queryBus.streamingQuery(any()))
                .thenReturn(Flux.error(new IllegalStateException("test")));

        StepVerifier.create(testSubject.streamingQuery(QUERY_PAYLOAD, String.class))
                    .expectErrorMatches(t -> t instanceof IllegalStateException && t.getMessage().equals("test"))
                    .verify();
    }

    @Test
    void dispatchStreamingQueryWithMetadata() {
        when(queryBus.streamingQuery(any())).thenReturn(Flux.empty());

        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                QUERY_TYPE, "Query", String.class
        ).andMetadata(Metadata.with("key", "value"));

        StepVerifier.create(testSubject.streamingQuery(testQuery, String.class))
                    .verifyComplete();

        verify(queryBus).streamingQuery(argThat(
                streamingQuery -> "value".equals(streamingQuery.metadata().get("key"))
        ));
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private <Q, R> QueryMessage anyMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    private <Q, R> SubscriptionQueryMessage<Q, R, R> anySubscriptionMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }
}
