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

package org.axonframework.reactive;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.*;
import org.mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultReactiveQueryGateway}.
 *
 * @author Milan Savic
 */
public class DefaultReactiveQueryGatewayTest {

    private QueryBus queryBus;
    private DefaultReactiveQueryGateway queryGateway;
    private QueryResponseMessage<Object> answer;
    private MessageDispatchInterceptor<QueryMessage<?, ?>> dispatchInterceptor;
    private String query;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        query = "query";
        queryBus = mock(QueryBus.class);
        dispatchInterceptor = mock(MessageDispatchInterceptor.class);
        queryGateway = new DefaultReactiveQueryGateway(queryBus, dispatchInterceptor);
        answer = new GenericQueryResponseMessage<>("answer");
    }

    @After
    public void checkInterceptor() {
        verify(dispatchInterceptor).handle(argThat((ArgumentMatcher<QueryMessage<String, String>>) x -> "query"
                .equals(x.getPayload())));
    }

    @Test
    public void testQuerySuccessful() {
        when(queryBus.query(any())).thenReturn(CompletableFuture.completedFuture(answer));

        String result = queryGateway.query(query, String.class).block();

        assertEquals("answer", result);
    }

    @Test
    public void testQueryFailed() {
        CompletableFuture<QueryResponseMessage<Object>> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new RuntimeException());
        when(queryBus.query(any())).thenReturn(completableFuture);

        String result = queryGateway.query(query, String.class).onErrorReturn("error").block();

        assertEquals("error", result);
    }

    @Test
    public void testScatterGather() {
        when(queryBus.scatterGather(any(), anyLong(), any(TimeUnit.class))).thenReturn(Stream.of(answer));

        String result = queryGateway.scatterGather(query,
                                                   ResponseTypes.instanceOf(String.class),
                                                   1,
                                                   TimeUnit.MILLISECONDS)
                                    .blockFirst();

        assertEquals("answer", result);
    }

    @Test
    public void testSubscriptionQuery() {
        when(queryBus.subscriptionQuery(any(), any())).thenAnswer(invocation -> {
            UpdateHandler<List<String>, String> updateHandler = invocation.getArgument(1);
            updateHandler.onInitialResult(asList("result1", "result2"));
            updateHandler.onUpdate("update");
            updateHandler.onCompleted();
            return (Registration) () -> true;
        });

        List<String> results = queryGateway.subscriptionQuery(query, String.class).collectList().block();

        assertEquals(asList("result1", "result2", "update"), results);
    }
}
