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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Abstract implementation of an {@link EventBus} that delegates all calls to a given delegate.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public abstract class DelegatingEventBus implements EventBus {

    protected final EventBus delegate;

    /**
     * Constructs the {@code DelegatingEventBus} with the given {@code delegate} to receive calls.
     *
     * @param delegate The {@link EventBus} instance to delegate calls to.
     */
    public DelegatingEventBus(@Nonnull EventBus delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate EventBus may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        return delegate.publish(context, events);
    }

    @Override
    public Registration subscribe(@Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
        return delegate.subscribe(eventsBatchConsumer);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        delegate.describeTo(descriptor);
    }
}
