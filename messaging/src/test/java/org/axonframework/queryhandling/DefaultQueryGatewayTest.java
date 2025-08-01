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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class verifying correct workings of the {@link DefaultQueryGateway}.
 *
 * @author Allard Buijze
 */
class DefaultQueryGatewayTest {

    private QueryBus mockBus;
    private DefaultQueryGateway testSubject;
    private QueryResponseMessage<String> answer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        answer = new GenericQueryResponseMessage<>(new MessageType("query"), "answer");
        MessageDispatchInterceptor<QueryMessage<?, ?>> mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
        mockBus = mock(QueryBus.class);
        testSubject = DefaultQueryGateway.builder()
                                         .queryBus(mockBus)
                                         .dispatchInterceptors(mockDispatchInterceptor)
                                         .build();
        when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    void pointToPointQuery() throws Exception {
        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));

        CompletableFuture<String> queryResponse = testSubject.query("query", String.class);
        assertEquals("answer", queryResponse.get());

        //noinspection unchecked
        ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(mockBus).query(queryMessageCaptor.capture());

        QueryMessage<String, String> result = queryMessageCaptor.getValue();
        assertEquals("query", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        assertEquals(MetaData.emptyInstance(), result.getMetaData());
    }

    @Test
    void pointToPointQueryWithMetaData() throws Exception {
        String expectedMetaDataKey = "key";
        String expectedMetaDataValue = "value";

        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));

        GenericMessage<String> testQuery = new GenericMessage<>(
                new MessageType("query"),
                "query",
                MetaData.with(expectedMetaDataKey, expectedMetaDataValue)
        );

        CompletableFuture<String> queryResponse = testSubject.query(testQuery, instanceOf(String.class));
        assertEquals("answer", queryResponse.get());

        //noinspection unchecked
        ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(mockBus).query(queryMessageCaptor.capture());

        QueryMessage<String, String> result = queryMessageCaptor.getValue();
        assertEquals("query", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        MetaData resultMetaData = result.getMetaData();
        assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
        assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
    }

    @Test
    void pointToPointQueryWhenQueryBusReportsAnError() throws Exception {
        Throwable expected = new Throwable("oops");
        QueryResponseMessage<String> testQuery = new GenericQueryResponseMessage<>(
                new MessageType("query"), expected, String.class
        );
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(completedFuture(testQuery));

        CompletableFuture<String> result = testSubject.query("query", String.class);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void pointToPointQueryWhenClientCancelQuery() {
        CompletableFuture<QueryResponseMessage<String>> queryBusFutureResult = new CompletableFuture<>();
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(queryBusFutureResult);

        CompletableFuture<String> result = testSubject.query("query", String.class);
        assertFalse(queryBusFutureResult.isDone());
        result.cancel(true);

        assertTrue(queryBusFutureResult.isDone());
        assertTrue(queryBusFutureResult.isCancelled());
    }

    @Test
    void pointToPointQueryWhenQueryBusThrowsException() throws Exception {
        Throwable expected = new Throwable("oops");
        CompletableFuture<QueryResponseMessage<String>> queryResponseCompletableFuture = new CompletableFuture<>();
        queryResponseCompletableFuture.completeExceptionally(expected);
        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(queryResponseCompletableFuture);

        CompletableFuture<String> result = testSubject.query("query", String.class);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void scatterGatherQuery() {
        long expectedTimeout = 1L;
        TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<String> queryResponse =
                testSubject.scatterGather("scatterGather", instanceOf(String.class), expectedTimeout, expectedTimeUnit);
        Optional<String> firstResult = queryResponse.findFirst();
        assertTrue(firstResult.isPresent());
        assertEquals("answer", firstResult.get());

        //noinspection unchecked
        ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(mockBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

        QueryMessage<String, String> result = queryMessageCaptor.getValue();
        assertEquals("scatterGather", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        assertEquals(MetaData.emptyInstance(), result.getMetaData());
    }

    @Test
    void scatterGatherQueryWithMetaData() {
        String expectedMetaDataKey = "key";
        String expectedMetaDataValue = "value";
        long expectedTimeout = 1L;
        TimeUnit expectedTimeUnit = TimeUnit.SECONDS;

        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Message<String> testQuery = new GenericMessage<>(
                new MessageType("query"), "scatterGather",
                MetaData.with(expectedMetaDataKey, expectedMetaDataValue)
        );

        Stream<String> queryResponse =
                testSubject.scatterGather(testQuery, instanceOf(String.class), expectedTimeout, expectedTimeUnit);
        Optional<String> firstResult = queryResponse.findFirst();
        assertTrue(firstResult.isPresent());
        assertEquals("answer", firstResult.get());

        //noinspection unchecked
        ArgumentCaptor<QueryMessage<String, String>> queryMessageCaptor = ArgumentCaptor.forClass(QueryMessage.class);

        verify(mockBus).scatterGather(queryMessageCaptor.capture(), eq(expectedTimeout), eq(expectedTimeUnit));

        QueryMessage<String, String> result = queryMessageCaptor.getValue();
        assertEquals("scatterGather", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        MetaData resultMetaData = result.getMetaData();
        assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
        assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
    }

    @Test
    void subscriptionQuery() {
        when(mockBus.subscriptionQuery(any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

        testSubject.subscriptionQuery("subscription", instanceOf(String.class), instanceOf(String.class));

        //noinspection unchecked
        ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor =
                ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

        verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

        SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
        assertEquals("subscription", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
        assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
        assertEquals(MetaData.emptyInstance(), result.getMetaData());
    }

    @Test
    void subscriptionQueryWithMetaData() {
        String expectedMetaDataKey = "key";
        String expectedMetaDataValue = "value";

        when(mockBus.subscriptionQuery(any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));


        Message<String> testQuery = new GenericMessage<>(
                new MessageType("query"), "subscription",
                MetaData.with(expectedMetaDataKey, expectedMetaDataValue)
        );
        testSubject.subscriptionQuery(testQuery, instanceOf(String.class), instanceOf(String.class));

        //noinspection unchecked
        ArgumentCaptor<SubscriptionQueryMessage<String, String, String>> queryMessageCaptor =
                ArgumentCaptor.forClass(SubscriptionQueryMessage.class);

        verify(mockBus).subscriptionQuery(queryMessageCaptor.capture(), anyInt());

        SubscriptionQueryMessage<String, String, String> result = queryMessageCaptor.getValue();
        assertEquals("subscription", result.payload());
        assertEquals(String.class, result.getPayloadType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getResponseType().getClass()));
        assertEquals(String.class, result.getResponseType().getExpectedResponseType());
        assertTrue(InstanceResponseType.class.isAssignableFrom(result.getUpdateResponseType().getClass()));
        assertEquals(String.class, result.getUpdateResponseType().getExpectedResponseType());
        MetaData resultMetaData = result.getMetaData();
        assertTrue(resultMetaData.containsKey(expectedMetaDataKey));
        assertTrue(resultMetaData.containsValue(expectedMetaDataValue));
    }

    @Test
    void dispatchInterceptor() {
        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(completedFuture(answer));
        testSubject.registerDispatchInterceptor(messages -> (integer, queryMessage) -> new GenericQueryMessage<>(
                new MessageType(queryMessage.type().name()),
                "dispatch-" + queryMessage.payload(), queryMessage.getResponseType())
        );

        testSubject.query("query", String.class).join();

        verify(mockBus).query(
                argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "dispatch-query".equals(x.payload()))
        );
    }

    @Test
    void exceptionInInitialResultOfSubscriptionQueryReportedInMono() {
        QueryResponseMessage<String> testResponse = new GenericQueryResponseMessage<>(
                new MessageType("query"), new MockException(), String.class
        );
        when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
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
        QueryResponseMessage<String> testQuery = new GenericQueryResponseMessage<>(
                new MessageType("query"), (String) null, String.class
        );
        when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
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
        SubscriptionQueryUpdateMessage<String> testQuery = new GenericSubscriptionQueryUpdateMessage<>(
                new MessageType("query"), null, String.class
        );
        when(mockBus.subscriptionQuery(anySubscriptionMessage(String.class, String.class), anyInt()))
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
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(completedFuture(new GenericQueryResponseMessage<>(
                        new MessageType("query"), "test"
                ) {
                    @Override
                    public String payload() {
                        throw new MockException("Faking serialization problem");
                    }
                }));

        CompletableFuture<String> actual = testSubject.query("query", String.class);
        assertTrue(actual.isDone());
        assertTrue(actual.isCompletedExceptionally());
        assertEquals("Faking serialization problem", actual.exceptionally(Throwable::getMessage).get());
    }

    @Test
    void streamingQueryIsLazy() {
        Publisher<QueryResponseMessage<Object>> response = Flux.just(
                new GenericQueryResponseMessage<>(new MessageType("query"), "a"),
                new GenericQueryResponseMessage<>(new MessageType("query"), "b"),
                new GenericQueryResponseMessage<>(new MessageType("query"), "c")
        );

        when(mockBus.streamingQuery(any()))
                .thenReturn(response);

        //first try without subscribing
        testSubject.streamingQuery("query", String.class);

        //expect query never sent
        verify(mockBus, never()).streamingQuery(any());

        //second try with subscribing
        StepVerifier.create(testSubject.streamingQuery("query", String.class))
                    .expectNext("a", "b", "c")
                    .verifyComplete();

        //expect query sent
        verify(mockBus, times(1)).streamingQuery(any(StreamingQueryMessage.class));
    }

    @Test
    void streamingQueryPropagateErrors() {
        when(mockBus.streamingQuery(any()))
                .thenReturn(Flux.error(new IllegalStateException("test")));

        StepVerifier.create(testSubject.streamingQuery("query", String.class))
                    .expectErrorMatches(t -> t instanceof IllegalStateException && t.getMessage().equals("test"))
                    .verify();
    }

    @Test
    void dispatchStreamingQueryWithMetaData() {
        when(mockBus.streamingQuery(any())).thenReturn(Flux.empty());

        StreamingQueryMessage<String, String> testQuery = new GenericStreamingQueryMessage<>(
                new MessageType("query"), "Query", String.class
        ).andMetaData(MetaData.with("key", "value"));

        StepVerifier.create(testSubject.streamingQuery(testQuery, String.class))
                    .verifyComplete();

        verify(mockBus).streamingQuery(argThat(
                streamingQuery -> "value".equals(streamingQuery.getMetaData().get("key"))
        ));
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private <Q, R> QueryMessage<Q, R> anyMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    private <Q, R> SubscriptionQueryMessage<Q, R, R> anySubscriptionMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }
}
