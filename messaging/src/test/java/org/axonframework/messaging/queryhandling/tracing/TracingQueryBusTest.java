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

package org.axonframework.messaging.queryhandling.tracing;

// TODO #3594 - Introduce tracing test logic here.
class TracingQueryBusTest {
    //private TestSpanFactory spanFactory;
    //private QueryBusSpanFactory queryBusSpanFactory;
    //    private QueryUpdateEmitterSpanFactory queryUpdateEmitterSpanFactory;

//    @Test
//    void querySingleIsTraced() throws ExecutionException, InterruptedException {
//        QueryMessage testQuery = new GenericQueryMessage(
//                new MessageType(String.class), "hello", singleStringResponse
//        );
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            spanFactory.verifySpanActive("QueryBus.query", testQuery);
//            return q.payload() + "1234";
//        });
//
//        testSubject.query(testQuery).get();
//
//        spanFactory.verifySpanCompleted("QueryBus.query", testQuery);
//    }

//    @Test
//    void ScatterGatherIsTraced() {
//        QueryMessage testQuery = new GenericQueryMessage(
//                new MessageType(String.class), "hello", multipleStringResponse
//        );
//
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQuery);
//            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-0");
//            return q.payload() + "1234";
//        });
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQuery);
//            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-1");
//            return q.payload() + "12345678";
//        });
//
//        //noinspection ResultOfMethodCallIgnored
//        testSubject.scatterGather(testQuery, 500, TimeUnit.MILLISECONDS).toList();
//
//        spanFactory.verifySpanCompleted("QueryBus.scatterGatherQuery", testQuery);
//        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-0");
//        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-1");
//    }

//    @Test
//    void queryUnknown() throws Exception {
//        QueryMessage testQuery = new GenericQueryMessage(
//                new MessageType(String.class), "hello", singleStringResponse
//        );
//        CompletableFuture<?> result = testSubject.query(testQuery);
//
//        try {
//            result.get();
//            fail("Expected exception");
//        } catch (ExecutionException e) {
//            assertEquals(NoHandlerForQueryException.class, e.getCause().getClass());
//        }
//        spanFactory.verifySpanHasException("QueryBus.query", NoHandlerForQueryException.class);
//    }

//    @Test
//    void subscriptionQueryIsTraced() throws InterruptedException {
//        CountDownLatch updatedLatch = new CountDownLatch(2);
//        final AtomicLong value = new AtomicLong();
//        testSubject.subscribe("queryName", Long.class, (q, ctx) -> value.get());
//        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
//        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(20))
//                                    .doOnNext(next -> {
//                                        updatedLatch.countDown();
//                                        updateEmitter.emit(query -> "queryName".equals(query.type().name()), next);
//                                    })
//                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.type().name())))
//                                    .subscribe();
//
//
//        SubscriptionQueryMessage<String, Long, Long> testQuery = new GenericSubscriptionQueryMessage<>(
//                new MessageType("queryName"), "test",
//                instanceOf(Long.class), instanceOf(Long.class)
//        );
//        try {
//            SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
//                    testSubject.subscriptionQuery(testQuery);
//            Mono<QueryResponseMessage> initialResult = result.initialResult();
//            Objects.requireNonNull(initialResult.block()).payload();
//            spanFactory.verifySpanCompleted("QueryBus.query");
//            updatedLatch.await();
//            Objects.requireNonNull(result.update().next().block()).payload();
//            spanFactory.verifySpanCompleted("QueryUpdateEmitter.emitQueryUpdateMessage");
//        } finally {
//            disposable.dispose();
//        }
//    }


}