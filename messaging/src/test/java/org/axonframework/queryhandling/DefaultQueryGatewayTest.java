/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.*;
import org.mockito.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultQueryGatewayTest {

    private QueryBus mockBus;
    private DefaultQueryGateway testSubject;
    private QueryResponseMessage<String> answer;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        answer = new GenericQueryResponseMessage<>("answer");
        MessageDispatchInterceptor<QueryMessage<?, ?>> mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
        mockBus = mock(QueryBus.class);
        testSubject = DefaultQueryGateway.builder()
                                         .queryBus(mockBus)
                                         .dispatchInterceptors(mockDispatchInterceptor)
                                         .build();
        when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    public void testDispatchSingleResultQuery() throws Exception {
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(CompletableFuture.completedFuture(answer));

        CompletableFuture<String> actual = testSubject.query("query", String.class);
        assertEquals("answer", actual.get());

        verify(mockBus).query(
                argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "query".equals(x.getPayload()))
        );
    }

    @Test
    public void testDispatchSingleResultQueryWhenBusReportsAnError() throws Exception {
        Throwable expected = new Throwable("oops");
        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(CompletableFuture
                                                                                       .completedFuture(new GenericQueryResponseMessage<>(
                                                                                               String.class,
                                                                                               expected)));
        CompletableFuture<String> result = testSubject.query("query", String.class);
        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
    }

    @Test
    public void testDispatchSingleResultQueryWhenBusThrowsException() throws Exception {
        Throwable expected = new Throwable("oops");
        CompletableFuture<QueryResponseMessage<String>> queryResponseMessageCompletableFuture = new CompletableFuture<>();
        queryResponseMessageCompletableFuture.completeExceptionally(expected);
        when(mockBus.query(anyMessage(String.class, String.class))).thenReturn(queryResponseMessageCompletableFuture);
        CompletableFuture<String> result = testSubject.query("query", String.class);
        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(expected.getMessage(), result.exceptionally(Throwable::getMessage).get());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDispatchMultiResultQuery() {
        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<String> actual = testSubject.scatterGather(
                "query", ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS
        );
        assertEquals("answer", actual.findFirst().get());
        verify(mockBus).scatterGather(
                argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "query".equals(x.getPayload())),
                eq(1L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    public void testDispatchSubscriptionQuery() {
        when(mockBus.subscriptionQuery(any(), any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

        testSubject.subscriptionQuery("query",
                                      ResponseTypes.instanceOf(String.class),
                                      ResponseTypes.instanceOf(String.class));
        verify(mockBus)
                .subscriptionQuery(argThat((ArgumentMatcher<SubscriptionQueryMessage<String, String, String>>)
                                                   x -> "query".equals(x.getPayload())), any(), anyInt());
    }

    @Test
    public void testDispatchInterceptor() {
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(CompletableFuture.completedFuture(answer));
        testSubject.registerDispatchInterceptor(messages -> (integer, queryMessage) -> new GenericQueryMessage<>(
                "dispatch-" + queryMessage.getPayload(),
                queryMessage.getQueryName(),
                queryMessage.getResponseType()));

        testSubject.query("query", String.class).join();

        verify(mockBus).query(
                argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "dispatch-query".equals(x.getPayload()))
        );
    }

    @SuppressWarnings("unused")
    private <Q, R> QueryMessage<Q, R> anyMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }
}
