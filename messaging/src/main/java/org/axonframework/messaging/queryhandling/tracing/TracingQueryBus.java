/*
 * Copyright (c) 2010-2026. Axon Framework
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

// TODO 3594 - Introduce tracing logic here.
public class TracingQueryBus {

    /*
        @Override
    public CompletableFuture<QueryResponseMessage> query(@Nonnull QueryMessage query) {
        Span span = spanFactory.createQuerySpan(query, false);
        return span.runSupplier(() -> doQuery(query).whenComplete((r, t) -> {
            if (t != null) {
                span.recordException(t);
            }
        }));
    }
    */

    /*
    private ResultMessage interceptAndInvokeStreaming(
            StreamingQueryMessage query,
            MessageHandler<? super StreamingQueryMessage, ? extends QueryResponseMessage> handler, Span span) {
        try (SpanScope unused = span.makeCurrent()) {
            LegacyDefaultUnitOfWork<StreamingQueryMessage> uow = LegacyDefaultUnitOfWork.startAndGet(query);
            return uow.executeWithResult((ctx) -> {
                /*
                // TODO 3594 - Reintegrate, and construct chain only once!
                QueryHandler queryHandler = new QueryHandler() {
                    @Nonnull
                    @Override
                    public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                                         @Nonnull ProcessingContext context) {
                        return handler.handle((StreamingQueryMessage<Q, R>)query, context).cast();
                    }
                };
                Object queryResponse = new QueryMessageHandlerInterceptorChain(handlerInterceptors, queryHandler)
                        .proceed(uow.getMessage(), ctx);

                 */
//    Object queryResponse = handler.handleSync(uow.getMessage(), ctx);
//                return Flux.from(query.responseType().convert(queryResponse))
//            .map(this::asResponseMessage);
//});
//        }
//        }

    /*
    UpdateEmitter logic

    @Test
    void queryUpdateEmitterIsTraced() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandler result = queryBus.subscribeToUpdates(
                queryMessage,
                1024
        );

        result.update().subscribe();
//        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        spanFactory.verifySpanCompleted("QueryUpdateEmitter.scheduleQueryUpdateMessage");
        spanFactory.verifySpanHasType("QueryUpdateEmitter.scheduleQueryUpdateMessage",
                                      TestSpanFactory.TestSpanType.INTERNAL);
        spanFactory.verifySpanCompleted("QueryUpdateEmitter.emitQueryUpdateMessage");
        spanFactory.verifySpanHasType("QueryUpdateEmitter.emitQueryUpdateMessage",
                                      TestSpanFactory.TestSpanType.DISPATCH);
    }
    * */
}
