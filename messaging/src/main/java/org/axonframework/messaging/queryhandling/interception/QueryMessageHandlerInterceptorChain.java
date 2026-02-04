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

package org.axonframework.messaging.queryhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryHandler;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MessageHandlerInterceptorChain} that intercepts {@link QueryMessage QueryMessages} for
 * {@link QueryHandler QueryHandlers}.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class QueryMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<QueryMessage> {

    private final QueryHandler interceptingHandler;

    /**
     * Constructs a new {@code QueryMessageHandlerInterceptorChain} with a list of {@code interception} and an
     * {@code queryHandler}.
     *
     * @param interceptors The list of handler interception that are part of this chain.
     * @param queryHandler The query handler to be invoked at the end of the interceptor chain.
     */
    public QueryMessageHandlerInterceptorChain(@Nonnull List<MessageHandlerInterceptor<? super QueryMessage>> interceptors,
                                               @Nonnull QueryHandler queryHandler) {
        Iterator<MessageHandlerInterceptor<? super QueryMessage>> interceptorIterator =
                new LinkedList<>(interceptors).descendingIterator();
        QueryHandler handler = Objects.requireNonNull(queryHandler, "The Query Handler may not be null.");
        while (interceptorIterator.hasNext()) {
            handler = new InterceptingHandler(interceptorIterator.next(), handler);
        }
        this.interceptingHandler = handler;
    }

    @Nonnull
    @Override
    public MessageStream<?> proceed(@Nonnull QueryMessage query, @Nonnull ProcessingContext context) {
        try {
            return interceptingHandler.handle(query, context);
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    private record InterceptingHandler(
            MessageHandlerInterceptor<? super QueryMessage> interceptor,
            QueryHandler next
    ) implements QueryHandler, MessageHandlerInterceptorChain<QueryMessage> {

        @Nonnull
        @Override
        public MessageStream<QueryResponseMessage> handle(@Nonnull QueryMessage query,
                                                          @Nonnull ProcessingContext context) {
            // noinspection unchecked,rawtypes
            return interceptor.interceptOnHandle(query, context, (MessageHandlerInterceptorChain) this);
        }

        @Nonnull
        @Override
        public MessageStream<?> proceed(@Nonnull QueryMessage query, @Nonnull ProcessingContext context) {
            return next.handle(query, context);
        }
    }
}
