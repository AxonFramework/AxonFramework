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

package org.axonframework.queryhandling.interceptors;

import org.axonframework.queryhandling.QueryExecutionException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

// TODO 3488 - Introduce handler and dispatch interceptor logic here.
public class InterceptingQueryBus {

    //    private final List<MessageHandlerInterceptor<QueryMessage>> handlerInterceptors = new CopyOnWriteArrayList<>();
    //    private final List<MessageDispatchInterceptor<? super QueryMessage>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    //    private <Q, R, T extends QueryMessage> T intercept(T query) {
    //        /*
    //        // TODO #3488 - Reintegrate, and construct chain only once!
    //        return new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors)
    //                .proceed(query, null)
    //                .first()
    //                .<T>cast()
    //                .asMono()
    //                .map(MessageStream.Entry::message)
    //                .block();
    //         */
    //        return query;
    //    }

    /* StreamingQuery -
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

    /* Scatter gather
    private ResultMessage interceptAndInvoke(
            LegacyUnitOfWork<QueryMessage> uow,
            MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> handler
    ) {
        return uow.executeWithResult((ctx) -> {
            ResponseType<?> responseType = uow.getMessage().responseType();
            // TODO #3488 - Reintegrate, and construct chain only once!
            /*
            QueryHandler queryHandler = new QueryHandler() {
                @Nonnull
                @Override
                public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                                     @Nonnull ProcessingContext context) {
                    return handler.handle((QueryMessage<?, R>)query, context).cast();
                }
            };
            Object queryResponse = new QueryMessageHandlerInterceptorChain(handlerInterceptors, queryHandler).proceed(uow.getMessage(), ctx);
             */
//    Object queryResponse = handler.handleSync(uow.getMessage(), ctx);
//            if (queryResponse instanceof CompletableFuture) {
//        return ((CompletableFuture<?>) queryResponse).thenCompose(
//                result -> buildCompletableFuture(responseType, result));
//    } else if (queryResponse instanceof Future) {
//        return CompletableFuture.supplyAsync(() -> {
//            try {
//                return asNullableResponseMessage(
//                        responseType.responseMessagePayloadType(),
//                        responseType.convert(((Future<?>) queryResponse).get()));
//            } catch (InterruptedException | ExecutionException e) {
//                throw new QueryExecutionException("Error happened while trying to execute query handler", e);
//            }
//        });
//    }
//            return buildCompletableFuture(responseType, queryResponse);
//});
//        }

    //    /**
    //     * Registers an interceptor that is used to intercept Queries before they are passed to their respective handlers.
    //     * The interceptor is invoked separately for each handler instance (in a separate unit of work).
    //     *
    //     * @param interceptor the interceptor to invoke before passing a Query to the handler
    //     * @return handle to deregister the interceptor
    //     */
    //    public Registration registerHandlerInterceptor(
    //            @Nonnull MessageHandlerInterceptor<QueryMessage> interceptor) {
    //        handlerInterceptors.add(interceptor);
    //        return () -> handlerInterceptors.remove(interceptor);
    //    }
    //
    //    /**
    //     * Registers an interceptor that intercepts Queries as they are sent. Each interceptor is called once, regardless of
    //     * the type of query (point-to-point or scatter-gather) executed.
    //     *
    //     * @param interceptor the interceptor to invoke when sending a Query
    //     * @return handle to deregister the interceptor
    //     */
    //    public @Nonnull
    //    Registration registerDispatchInterceptor(
    //            @Nonnull MessageDispatchInterceptor<? super QueryMessage> interceptor) {
    //        dispatchInterceptors.add(interceptor);
    //        return () -> dispatchInterceptors.remove(interceptor);
    //    }

}
