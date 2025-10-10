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

package org.axonframework.queryhandling.intercepting;

// TODO 3488 - Implement once InterceptingQueryBus is in place
class InterceptingQueryBusTest {

    /* Correlation data tests
    @Disabled("TODO: reintegrate as part of #3079")
        @Test
        void directQueryResultsInEmptyMessageStream() throws ExecutionException, InterruptedException {
            QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "query", SINGLE_STRING_RESPONSE);
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, (query, context) -> MessageStream.empty().cast());
            QueryMessage testQuery =
                    new GenericQueryMessage(new MessageType(String.class), "query", SINGLE_STRING_RESPONSE)
                            .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
            CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

            assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
            assertNull(result.get().payload());
            assertEquals(String.class, result.get().payloadType());
            assertEquals(
                    // TODO: this assumes the correlation and tracing data gets into response
                    // but this is done via interceptors, which are currently not integrated
                    MetaData.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
                    result.get().metaData()
            );
        }

        /*
         * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
         * contains the correlation data registered with the Unit of Work
         */
//    @Disabled("TODO: reintegrate as part of #3079")
//    @Test
//    void queryResultContainsCorrelationData() throws Exception {
////        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//
//        QueryMessage testQuery =
//                new GenericQueryMessage(new MessageType(String.class), "query", SINGLE_STRING_RESPONSE)
//                        .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
//        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);
//
//        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
//        assertEquals("query1234", result.get().payload());
//        assertEquals(
//                MetaData.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
//                result.get().metaData()
//        );
//    }

    /*
        @Test
    public void handlerInterceptorThrowsException() throws ExecutionException, InterruptedException {
        testSubject.subscribe("test", String.class, (q, ctx) -> q.payload().toString());
        testSubject.registerHandlerInterceptor(( message,context, chain) ->
            MessageStream.failed(new RuntimeException("Faking"))
        );
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("test"), "hello", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertTrue(result.get().isExceptional());
    }
     */

    /*
    @Test
    void queryWithInterceptors() throws Exception {
        testSubject.registerDispatchInterceptor(
                (message, context, chain) ->
                        chain.proceed(message.andMetaData(Collections.singletonMap("key", "value")), context)
        );
        testSubject.registerHandlerInterceptor((message, context, chain) -> {
            if (message.metaData().containsKey("key")) {
                return MessageStream.just(new GenericQueryResponseMessage(new MessageType("response"), "fakeReply"));
            }
            return chain.proceed(message, context);
        });
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertEquals("fakeReply", result.get());
    }
     */

    /*
    @Test
    void scatterGatherWithInterceptors() {
        testSubject.registerDispatchInterceptor(
                (message, context, chain) ->
                        chain.proceed(message.andMetaData(Collections.singletonMap("key", "value")), context)
        );
        testSubject.registerHandlerInterceptor((message, context, chain) -> {
            if (message.metaData().containsKey("key")) {
                return MessageStream.just(new GenericResultMessage(new MessageType("response"), "fakeReply"));
            }
            return chain.proceed(message, context);
        });
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        List<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
                                          .map(Message::payload)
                                          .collect(Collectors.toList());

        assertEquals(2, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertEquals(asList("fakeReply", "fakeReply"), results);
    }
     */

    /*
    @Test
    void queryResponseMessageCorrelationData() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        QueryResponseMessage queryResponseMessage = testSubject.query(testQuery).get();
        assertEquals(testQuery.identifier(), queryResponseMessage.metaData().get("traceId"));
        assertEquals(testQuery.identifier(), queryResponseMessage.metaData().get("correlationId"));
        assertEquals("Hello, World1234", queryResponseMessage.payload());
    }
     */

    /*
    @Disabled("TODO reintegrate as part of #3079")
    @Test
    void dispatchInterceptor() {
        AtomicBoolean hasBeenCalled = new AtomicBoolean();
        queryBus.registerDispatchInterceptor((message, context, chain) -> {
            hasBeenCalled.set(true);
            return chain.proceed(message, context);
        });

        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("fluxQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "b", "c", "d")
                    .verifyComplete();

        assertTrue(hasBeenCalled.get());
    }

    @Disabled("TODO reintegrate as part of #3079")
    @Test
    void handlerInterceptor() {
        queryBus.registerHandlerInterceptor(
                (message, context, chain) ->
                        chain.proceed(message, context)
                // TODO reintegrate as part of #3079
                        // ((Flux) interceptorChain.proceedSync(context)).map(it -> "a")
        );

        StreamingQueryMessage testQuery = new GenericStreamingQueryMessage(
                new MessageType("fluxQuery"), "criteria", String.class
        );

        StepVerifier.create(streamingQueryPayloads(testQuery, String.class))
                    .expectNext("a", "a", "a", "a")
                    .verifyComplete();
    }
     */

    /*
    Subscription query tests
    @Test
    @Disabled("TODO #3488")
    void subscriptionQueryWithInterceptors() {
        // given
        List<String> interceptedResponse = Arrays.asList("fakeReply1", "fakeReply2");
//        queryBus.registerDispatchInterceptor((message, context, chain) -> chain.proceed(
//                message.andMetadata(Collections.singletonMap("key", "value")), context
//        ));
//        queryBus.registerHandlerInterceptor((message, context, chain) -> {
//            if (message.metadata().containsKey("key")) {
//                return MessageStream.fromIterable(
//                        interceptedResponse.stream()
//                                           .map(p -> new GenericQueryResponseMessage(new MessageType("response"), p))
//                                           .toList()
//                );
//            }
//            return chain.proceed(message, context);
//        });
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);

        // then
        StepVerifier.create(result.initialResult().map(Message::payload))
                    .expectNext(interceptedResponse)
                    .verifyComplete();
    }

    @Test
    @Disabled("TODO #3488")
    void subscriptionQueryUpdateWithInterceptors() {
        // given
        Map<String, String> metadata = Collections.singletonMap("key", "value");
//        queryUpdateEmitter.registerDispatchInterceptor(
//                (message, context, chain) -> chain.proceed(message.andMetadata(metadata), context)
//        );
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);

        queryBus.emitUpdate(String.class, TEST_QUERY_PAYLOAD::equals, "Update1");
        result.close();

        // then
        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.metadata().equals(metadata))
                    .verifyComplete();
    }
     */
}