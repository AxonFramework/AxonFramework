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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MessageHandlerInterceptorChain} that intercepts {@link EventMessage EventMessages} for
 * {@link EventHandler EventHandlers}.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
public class EventMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<EventMessage> {

    private final EventHandler eventHandler;
    private final Iterator<MessageHandlerInterceptor<EventMessage>> chain;

    /**
     * Constructs a new {@code EventMessageHandlerInterceptorChain} with a list of {@code interceptors} and an
     * {@code eventHandler}.
     *
     * @param interceptors The list of handler interceptors that are part of this chain.
     * @param eventHandler The event handler to be invoked at the end of the interceptor chain.
     */
    public EventMessageHandlerInterceptorChain(
            @Nonnull List<MessageHandlerInterceptor<EventMessage>> interceptors,
            @Nonnull EventHandler eventHandler
    ) {
        this.chain = interceptors.iterator();
        this.eventHandler = Objects.requireNonNull(eventHandler, "The Event Handler may not be null.");
    }

    @Nonnull
    @Override
    public MessageStream<?> proceed(@Nonnull EventMessage event,
                                    @Nonnull ProcessingContext context) {
        try {
            if (chain.hasNext()) {
                return chain.next().interceptOnHandle(event, context, this);
            } else {
                return eventHandler.handle(event, context);
            }
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }
}
