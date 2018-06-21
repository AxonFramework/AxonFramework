/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
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
        testSubject = new DefaultQueryGateway(mockBus, mockDispatchInterceptor);
        when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    public void testDispatchSingleResultQuerySingleResponse() throws Exception {
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(CompletableFuture.completedFuture(answer));

        CompletableFuture<String> actual = testSubject.query("query", String.class);
        assertEquals("answer", actual.get());

        verify(mockBus).query(argThat((ArgumentMatcher<QueryMessage<String, String>>)
                                              x -> "query".equals(x.getPayload())
                                                      && "java.lang.String-java.lang.String".equals(x.getQueryName())));
    }

    @Test
    public void testDispatchSingleResultQueryMultipleResponses() throws Exception {
        when(mockBus.query(anyMessage(String.class, String.class)))
                .thenReturn(CompletableFuture.completedFuture(answer));

        CompletableFuture<List<String>> actual = testSubject.query(5,
                                                                   ResponseTypes.multipleInstancesOf(String.class));
        assertEquals("answer", actual.get());

        verify(mockBus).query(argThat((ArgumentMatcher<QueryMessage<Integer, String>>)
                                              x -> 5 == x.getPayload()
                                                      && "java.lang.Integer-java.lang.String".equals(x.getQueryName())));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDispatchMultiResultQuerySingleResponse() {
        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<String> actual = testSubject.scatterGather(
                "query", ResponseTypes.instanceOf(String.class), 1, TimeUnit.SECONDS
        );
        assertEquals("answer", actual.findFirst().get());
        verify(mockBus).scatterGather(argThat((ArgumentMatcher<QueryMessage<String, String>>)
                                                      x -> "query".equals(x.getPayload())
                                                        && "java.lang.String-java.lang.String".equals(x.getQueryName())),
                                      eq(1L), eq(TimeUnit.SECONDS));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDispatchMultiResultQueryMultipleResponses() {
        when(mockBus.scatterGather(anyMessage(String.class, String.class), anyLong(), any()))
                .thenReturn(Stream.of(answer));

        Stream<List<String>> actual = testSubject.scatterGather(
                5, ResponseTypes.multipleInstancesOf(String.class), 1, TimeUnit.SECONDS
        );
        assertEquals("answer", actual.findFirst().get());
        verify(mockBus).scatterGather(argThat((ArgumentMatcher<QueryMessage<Integer, List<String>>>)
                                                      x -> 5 == x.getPayload()
                                                              && "java.lang.Integer-java.lang.String".equals(x.getQueryName())),
                                      eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void testDispatchSubscriptionQueryMultipleInitialResponses() {
        when(mockBus.subscriptionQuery(any(), any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

        testSubject.subscriptionQuery(5,
                                      ResponseTypes.multipleInstancesOf(String.class),
                                      ResponseTypes.instanceOf(String.class));
        verify(mockBus)
                .subscriptionQuery(argThat((ArgumentMatcher<SubscriptionQueryMessage<Integer, List<String>, String>>)
                                                   x -> 5 == x.getPayload()
                                                    && "java.lang.Integer-java.lang.String".equals(x.getQueryName())),
                                   any(), anyInt());
    }

    @Test
    public void testDispatchSubscriptionQuerySingleInitialResponse() {
        when(mockBus.subscriptionQuery(any(), any(), anyInt()))
                .thenReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.empty(), () -> true));

        testSubject.subscriptionQuery(5,
                                      ResponseTypes.instanceOf(String.class),
                                      ResponseTypes.instanceOf(String.class));
        verify(mockBus)
                .subscriptionQuery(argThat((ArgumentMatcher<SubscriptionQueryMessage<Integer, String, String>>)
                                                   x -> 5 == x.getPayload()
                                                           && "java.lang.Integer-java.lang.String".equals(x.getQueryName())),
                                   any(), anyInt());
    }

    @SuppressWarnings("unused")
    private <Q, R> QueryMessage<Q, R> anyMessage(Class<Q> queryType, Class<R> responseType) {
        return any();
    }
}
