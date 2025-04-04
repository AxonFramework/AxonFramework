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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Default implementation of a DecoratorDefinition. Not to be used directly. Use the static methods on
 * {@link DecoratorDefinition} instead to construct an instance.
 *
 * @param <C> The declared type of the component
 * @param <D> The decorated type of the component
 */
class DefaultDecoratorDefinition<C, D extends C> implements
        DecoratorDefinition.CompletedDecoratorDefinition<C, D> {

    private final Predicate<Component.Identifier<?>> selector;
    private final ComponentDecorator<C, D> decorator;

    private final List<AbstractComponent.HandlerRegistration<D>> startHandlers = new CopyOnWriteArrayList<>();
    private final List<AbstractComponent.HandlerRegistration<D>> shutdownHandlers = new CopyOnWriteArrayList<>();
    private int order;

    /**
     * Initialize the DecoratorDefinition with given {@code selector} to identify the components requiring decoration
     * and given {@code decorator} to apply to these components.
     *
     * @param selector  The predicate defining which components to decorate
     * @param decorator The decorator function to apply to matching components
     */
    public DefaultDecoratorDefinition(Predicate<Component.Identifier<?>> selector, ComponentDecorator<C, D> decorator) {
        this.selector = selector;
        this.decorator = decorator;
    }

    @Override
    public Component<C> decorate(Component<C> delegate) {
        return new DecoratedComponent<>(delegate, decorator, startHandlers, shutdownHandlers);
    }

    @Override
    public boolean matches(Component.Identifier<?> componentId) {
        return selector.test(componentId);
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public DecoratorDefinition<C, D> order(int order) {
        this.order = order;
        return this;
    }

    @Override
    public DecoratorDefinition<C, D> onStart(int phase, ComponentLifecycleHandler<D> handler) {
        startHandlers.add(new AbstractComponent.HandlerRegistration<>(phase, handler));
        return this;
    }

    @Override
    public DecoratorDefinition<C, D> onShutdown(int phase, ComponentLifecycleHandler<D> handler) {
        shutdownHandlers.add(new AbstractComponent.HandlerRegistration<>(phase, handler));
        return this;
    }
}
