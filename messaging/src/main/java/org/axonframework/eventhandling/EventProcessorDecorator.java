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
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base decorator for {@link EventProcessor} implementations.
 * Forwards all calls to the delegate by default.
 * Subclasses can override methods to add cross-cutting behavior.
 */
public abstract class EventProcessorDecorator implements EventProcessor {
    protected final EventProcessor delegate;

    protected EventProcessorDecorator(EventProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return delegate.getHandlerInterceptors();
    }

    @Override
    public Registration registerHandlerInterceptor(@Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        return delegate.registerHandlerInterceptor(interceptor);
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void shutDown() {
        delegate.shutDown();
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        return delegate.shutdownAsync();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public boolean isError() {
        return delegate.isError();
    }
} 