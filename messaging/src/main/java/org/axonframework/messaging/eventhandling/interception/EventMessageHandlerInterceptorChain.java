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

package org.axonframework.messaging.eventhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link MessageHandlerInterceptorChain} that intercepts {@link EventMessage EventMessages} for
 * {@link EventHandler EventHandlers}.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class EventMessageHandlerInterceptorChain implements MessageHandlerInterceptorChain<EventMessage> {

    private final EventHandler interceptingHandler;

    /**
     * Constructs a new {@code EventMessageHandlerInterceptorChain} with a list of {@code interception} and an
     * {@code eventHandler}.
     *
     * @param interceptors The list of handler interceptors that are part of this chain.
     * @param eventHandler The event handler to be invoked at the end of the interceptor chain.
     */
    public EventMessageHandlerInterceptorChain(@Nonnull List<MessageHandlerInterceptor<? super EventMessage>> interceptors,
                                               @Nonnull EventHandler eventHandler) {
        Iterator<MessageHandlerInterceptor<? super EventMessage>> interceptorIterator =
                new LinkedList<>(interceptors).descendingIterator();
        EventHandler interceptingHandler = Objects.requireNonNull(eventHandler, "The Event Handler may not be null.");
        while (interceptorIterator.hasNext()) {
            interceptingHandler = new InterceptingHandler(interceptorIterator.next(), interceptingHandler);
        }
        this.interceptingHandler = interceptingHandler;
    }

    @Override
    @Nonnull
    public MessageStream<?> proceed(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        try {
            return interceptingHandler.handle(event, context);
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    private record InterceptingHandler(
            MessageHandlerInterceptor<? super EventMessage> interceptor,
            EventHandler next
    ) implements EventHandler, MessageHandlerInterceptorChain<EventMessage> {

        @Override
        @Nonnull
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            //noinspection unchecked,rawtypes
            return interceptor.interceptOnHandle(event, context, (MessageHandlerInterceptorChain) this)
                              .ignoreEntries();
        }

        @Override
        @Nonnull
        public MessageStream<?> proceed(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            return next.handle(event, context);
        }
    }
}
