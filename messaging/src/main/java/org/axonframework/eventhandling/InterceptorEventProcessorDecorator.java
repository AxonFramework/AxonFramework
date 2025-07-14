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
import org.axonframework.common.Registration;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decorator for {@link EventProcessor} that manages handler interceptors and applies them in a chain.
 */
public class InterceptorEventProcessorDecorator extends EventProcessorDecorator {
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();

    public InterceptorEventProcessorDecorator(EventProcessor delegate) {
        super(delegate);
    }

    @Override
    public Registration registerHandlerInterceptor(@Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return List.copyOf(interceptors);
    }

    /**
     * Process a batch of events through the interceptor chain, then delegate to the core processor.
     *
     * @param eventMessages The batch of messages to process
     * @param context The processing context
     * @param handler The handler to invoke after interceptors
     * @throws Exception if processing fails
     */
    protected void processWithInterceptors(List<? extends EventMessage<?>> eventMessages,
                                           ProcessingContext context,
                                           EventBatchHandler handler) throws Exception {
        DefaultInterceptorChain<EventMessage<?>, ?> chain =
                new DefaultInterceptorChain<>(null, interceptors, (msg, ctx) -> handler.handle(List.of(msg), ctx));
        for (EventMessage<?> message : eventMessages) {
            chain.proceed(message, context);
        }
    }

    /**
     * Functional interface for handling a batch of events after interceptors.
     */
    @FunctionalInterface
    public interface EventBatchHandler {
        Object handle(List<? extends EventMessage<?>> eventMessages, ProcessingContext context) throws Exception;
    }
} 