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

package org.axonframework.configuration;

import java.util.Objects;

import jakarta.annotation.Nonnull;

/**
 * A {@link NewConfigurer} implementation delegating all calls to a {@code delegate Configurer}.
 *
 * @param <S> The type of configurer this implementation returns. This generic allows us to support fluent interfacing.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelegatingConfigurer<S extends NewConfigurer<S>> implements NewConfigurer<S> {

    private final NewConfigurer<?> delegate;

    /**
     * Construct a {@code DelegatingConfigurer} using the given {@code delegate} to delegate all operations to.
     *
     * @param delegate The configurer to delegate all operations too.
     */
    public DelegatingConfigurer(@Nonnull NewConfigurer<?> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "A parent configuration is required");
    }

    @Override
    public <C> S registerComponent(@Nonnull Class<C> type,
                                   @Nonnull String name,
                                   @Nonnull ComponentBuilder<C> builder) {
        delegate.registerComponent(type, name, builder);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public <C> S registerDecorator(@Nonnull Class<C> type,
                                   @Nonnull String name,
                                   int order,
                                   @Nonnull ComponentDecorator<C> decorator) {
        delegate.registerDecorator(type, name, order, decorator);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type, @Nonnull String name) {
        return delegate.hasComponent(type, name);
    }

    @Override
    public S registerEnhancer(@Nonnull ConfigurerEnhancer enhancer) {
        delegate.registerEnhancer(enhancer);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public <M extends Module<M>> S registerModule(@Nonnull ModuleBuilder<M> builder) {
        delegate.registerModule(builder);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        delegate.onStart(phase, startHandler);
    }

    @Override
    public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        delegate.onShutdown(phase, shutdownHandler);
    }

    @Override
    public <C extends NewConfiguration> C build() {
        return delegate.build();
    }
}
