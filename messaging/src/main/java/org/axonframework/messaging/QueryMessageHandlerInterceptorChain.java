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

public class QueryMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<QueryMessage<?, ?>> {

    private final QueryHandler handler;
    private final Iterator<MessageHandlerInterceptor<QueryMessage<?, ?>>> chain;

    public QueryMessageHandlerInterceptorChain(@Nonnull List<MessageHandlerInterceptor<QueryMessage<?, ?>>> handlerInterceptors,
                                               @Nonnull QueryHandler handler) {
        this.handler = handler;
        this.chain = handlerInterceptors.iterator();
    }

    @Override
    public @Nonnull MessageStream<?> proceed(
            @Nonnull QueryMessage<?, ?> message,
            @Nonnull ProcessingContext context
    ) {
        if (chain.hasNext()) {
            return chain.next().interceptOnHandle(message, context, this);
        } else {
            return handler.handle(message, context);
        }
    }
}
