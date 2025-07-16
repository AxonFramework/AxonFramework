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

package org.axonframework.eventhandling.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;

/**
 * An EventHandlingComponent implementation that supports intercepting event handling through
 * MessageHandlerInterceptors. This component delegates actual event handling to another EventHandlingComponent while
 * applying configured interceptors to the message handling process.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
// TODO: Refactor to new interceptor support.
public class InterceptingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final MessageHandlerInterceptors messageHandlerInterceptors;

    /**
     * Constructs an InterceptingEventHandlingComponent with the given delegate and interceptors.
     *
     * @param delegate     The EventHandlingComponent to delegate to.
     * @param messageHandlerInterceptors The list of interceptors to initialize with.
     */
    public InterceptingEventHandlingComponent(@Nonnull EventHandlingComponent delegate,
                                              @Nonnull MessageHandlerInterceptors messageHandlerInterceptors) {
        super(delegate);
        this.messageHandlerInterceptors = Objects.requireNonNull(messageHandlerInterceptors, "InterceptorsList may not be null");
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        DefaultInterceptorChain<EventMessage<?>, ?> chain =
                new DefaultInterceptorChain<>(
                        null,
                        messageHandlerInterceptors.toList(),
                        delegate::handle
                );
        return chain.proceed(event, context).ignoreEntries().cast();
    }

}
