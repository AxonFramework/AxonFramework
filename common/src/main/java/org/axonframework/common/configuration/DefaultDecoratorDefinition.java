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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of a DecoratorDefinition.
 * <p>
 * Not to be used directly. Use the static methods on {@link DecoratorDefinition} instead to construct an instance.
 *
 * @param <C> The declared type of the component.
 * @param <D> The decorated type of the component.
 * @author Allard Buijze
 * @since 5.0.0
 */
class DefaultDecoratorDefinition<C, D extends C>
        implements DecoratorDefinition.CompletedDecoratorDefinition<C, D> {

    private final Predicate<Component.Identifier<?>> selector;
    private final ComponentDecorator<C, D> decorator;

    private int order;
    private final List<AbstractComponent.HandlerRegistration<D>> startHandlers = new CopyOnWriteArrayList<>();
    private final List<AbstractComponent.HandlerRegistration<D>> shutdownHandlers = new CopyOnWriteArrayList<>();

    /**
     * Initialize the DecoratorDefinition with given {@code selector} to identify the components requiring decoration
     * and given {@code decorator} to apply to these components.
     *
     * @param selector  The predicate defining which components to decorate.
     * @param decorator The decorator function to apply to matching components.
     */
    public DefaultDecoratorDefinition(@Nonnull Predicate<Component.Identifier<?>> selector,
                                      @Nonnull ComponentDecorator<C, D> decorator) {
        this.selector = requireNonNull(selector, "The selector must not be null.");
        this.decorator = requireNonNull(decorator, "The decorator must not be null.");
    }

    @Override
    public DecoratorDefinition<C, D> order(int order) {
        this.order = order;
        return this;
    }

    @Override
    public DecoratorDefinition<C, D> onStart(int phase, @Nonnull ComponentLifecycleHandler<D> handler) {
        startHandlers.add(new AbstractComponent.HandlerRegistration<>(phase, handler));
        return this;
    }

    @Override
    public DecoratorDefinition<C, D> onShutdown(int phase, @Nonnull ComponentLifecycleHandler<D> handler) {
        shutdownHandlers.add(new AbstractComponent.HandlerRegistration<>(phase, handler));
        return this;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public Component<C> decorate(@Nonnull Component<C> delegate) {
        return new DecoratedComponent<>(delegate, decorator, startHandlers, shutdownHandlers);
    }

    @Override
    public boolean matches(@Nonnull Component.Identifier<?> id) {
        return selector.test(id);
    }
}
