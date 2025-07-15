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

import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator for {@link EventProcessingPipeline} that applies handler interceptors in a chain.
 */
class InterceptorEventProcessingPipelineDecorator extends EventProcessingPipelineDecorator {
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors;

    public InterceptorEventProcessingPipelineDecorator(EventProcessingPipeline delegate, List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors) {
        super(delegate);
        this.interceptors = List.copyOf(interceptors);
    }

    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getInterceptors() {
        return List.copyOf(interceptors);
    }

    @Override
    public CompletableFuture<Void> processInUnitOfWork(
            List<? extends EventMessage<?>> eventMessages,
            UnitOfWork unitOfWork,
            Collection<Segment> processingSegments
    ) throws Exception {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (EventMessage<?> message : eventMessages) {
            DefaultInterceptorChain<EventMessage<?>, ?> chain =
                    new DefaultInterceptorChain<>(null, interceptors, (msg, ctx) ->
                            delegate.processInUnitOfWork(List.of(msg), unitOfWork, processingSegments));
            result = result.thenCompose(v -> chain.proceed(message, (org.axonframework.messaging.unitofwork.ProcessingContext) unitOfWork)
                                                  .ignoreEntries()
                                                  .asCompletableFuture()
                                                  .thenApply(e -> null));
        }
        return result;
    }
} 