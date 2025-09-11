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

package org.axonframework.queryhandling.tracing;

// TODO 3488 - Introduce tracing logic here.
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
                // TODO #3488 - Reintegrate, and construct chain only once!
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
    @Override
    public Stream<QueryResponseMessage> scatterGather(@Nonnull QueryMessage query, long timeout,
                                                      @Nonnull TimeUnit unit) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Scatter-Gather query does not support Flux as a return type.");
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlers =
                getHandlersForMessage(query);
        if (handlers.isEmpty()) {
            monitorCallback.reportIgnored();
            return Stream.empty();
        }

        return spanFactory.createScatterGatherSpan(query, false).runSupplier(() -> {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            List<Span> spans = handlers.stream().map(handler -> {
                int handlerIndex = handlers.indexOf(handler);
                return spanFactory.createScatterGatherHandlerSpan(query, handlerIndex);
            }).collect(Collectors.toList());
            return handlers
                    .stream()
                    .map(handler -> {
                        Span span = spans.get(handlers.indexOf(handler));
                        return span.runSupplier(
                                () -> scatterGatherHandler(span, monitorCallback, query, deadline, handler));
                    })
                    .filter(Objects::nonNull);
        });
    }
     */

    /*
    private QueryResponseMessage scatterGatherHandler(
            Span span,
            MessageMonitor.MonitorCallback monitorCallback,
            QueryMessage interceptedQuery,
            long deadline,
            MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> handler
    ) {
        long leftTimeout = getRemainingOfDeadline(deadline);
        ResultMessage resultMessage =
                interceptAndInvoke(LegacyDefaultUnitOfWork.startAndGet(interceptedQuery),
                                   handler);
        QueryResponseMessage response = null;
        if (resultMessage.isExceptional()) {
            monitorCallback.reportFailure(resultMessage.exceptionResult());
            span.recordException(resultMessage.exceptionResult());
            errorHandler.onError(resultMessage.exceptionResult(), interceptedQuery, handler);
        } else {
            try {
                response = ((CompletableFuture<QueryResponseMessage>) resultMessage.payload()).get(leftTimeout,
                                                                                                   TimeUnit.MILLISECONDS);
                monitorCallback.reportSuccess();
            } catch (Exception e) {
                span.recordException(e);
                monitorCallback.reportFailure(e);
                errorHandler.onError(e, interceptedQuery, handler);
            }
        }
        return response;
    }
     */
}
