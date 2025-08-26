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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MessageHandlerInterceptorChain} that intercepts {@link QueryMessage QueryMessages} for
 * {@link QueryHandler QueryHandlers}.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
public class QueryMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<QueryMessage> {

    private final QueryHandler queryHandler;
    private final Iterator<MessageHandlerInterceptor<QueryMessage>> chain;

    /**
     * Constructs a new {@code QueryMessageHandlerInterceptorChain} with a list of {@code interceptors} and an
     * {@code queryHandler}.
     *
     * @param interceptors The list of handler interceptors that are part of this chain.
     * @param queryHandler The query handler to be invoked at the end of the interceptor chain.
     */
    public QueryMessageHandlerInterceptorChain(
            @Nonnull List<MessageHandlerInterceptor<QueryMessage>> interceptors,
            @Nonnull QueryHandler queryHandler
    ) {
        this.chain = interceptors.iterator();
        this.queryHandler = Objects.requireNonNull(queryHandler, "The Query Handler may not be null.");
    }

    @Nonnull
    @Override
    public MessageStream<?> proceed(@Nonnull QueryMessage query,
                                             @Nonnull ProcessingContext context) {
        try {
            if (chain.hasNext()) {
                return chain.next().interceptOnHandle(query, context, this);
            } else {
                return queryHandler.handle(query, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
