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
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Iterator;
import java.util.List;

/**
 * Event handler interceptor chain.
 *
 * @since 5.0.0
 * @author Simon Zambrovski
 */
public class EventMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<EventMessage<?>> {

    private final EventHandler handler;
    private final Iterator<MessageHandlerInterceptor<EventMessage<?>>> chain;

    /**
     * Constructs a new chain with a list of interceptors and a query handler.
     * @param handlerInterceptors list of interceptors.
     * @param handler query handler.
     */
    public EventMessageHandlerInterceptorChain(@Nonnull List<MessageHandlerInterceptor<EventMessage<?>>> handlerInterceptors,
                                               @Nonnull EventHandler handler) {
        this.chain = handlerInterceptors.iterator();
        this.handler = handler;
    }

    @Override
    public @Nonnull MessageStream<?> proceed(
            @Nonnull EventMessage<?> message,
            @Nonnull ProcessingContext context
    ) {
        try {
            if (chain.hasNext()) {
                return chain.next().interceptOnHandle(message, context, this);
            } else {
                return handler.handle(message, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
