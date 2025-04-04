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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Defines the structure of a decorator for Components in the {@link NewConfiguration} of the application or one of its
 * Modules.
 * <p>
 * Decorators can wrap or replace the implementation of defined Components based on their type and optionally their
 * name. Decorators must return an instance that is an implementation of the declared type. Typically, they wrap another
 * component to provide additional behavior.
 * <p>
 * To create a decorator for a component of type {@code MyComponentInterface} with an implementation that has a
 * dependency, it would look as follows:
 * <pre><code>
 * DecoratorDefinition.forType(MyComponentInterface.class)
 *                    .with((cfg, name, delegate) -> new MyComponentWrapper(delegate, cfg.getComponent(MyDependency.class)))
 *                    .onStart(0, MyComponentWrapper::start)
 *                    .onShutdown(0, MyComponentWrapper::shutdown
 * </code></pre>
 * <p>
 * Alternatively, you can use
 * <pre><code>
 *     DecoratorDefinition.forTypeAndName(MyComponentInterface.class, "MyName")
 *                        .with(cfg -> ...)
 * </code></pre>
 * in case you need to only wrap a component with a given name.
 *
 * @param <C> The declared type of the component.
 * @param <D> The instance type of the decorated component.
 */
public sealed interface DecoratorDefinition<C, D extends C> permits DecoratorDefinition.CompletedDecoratorDefinition {

    /**
     * Initiates the configuration of a decorator for components with the given {@code type}, which must correspond with
     * the declared type of these components.
     * <p>
     * If multiple components have been defined for this type, they all are subject to decoration under this definition.
     * To decorate only a specific Component, consider using {@link #forTypeAndName(Class, String)}.
     *
     * @param type The class of the declared type of the components to decorate
     * @param <C>  The declared type of the components to decorate
     * @return a builder for further configuration of this definition
     */
    static <C> PartialDecoratorDefinition<C> forType(Class<C> type) {
        return new PartialDecoratorDefinition<>() {
            @Override
            public <D extends C> DecoratorDefinition<C, D> with(@Nonnull ComponentDecorator<C, D> decorator) {
                Objects.requireNonNull(decorator, "decorator must not be null");
                return new DefaultDecoratorDefinition<>(i ->
                                                                Objects.equals(type, i.type()), decorator);
            }
        };
    }

    /**
     * Initiates the configuration of a decorator for a component with the given {@code type}, which must correspond
     * with the declared type of these components, and {@code name}. The decorator is only invoked if such component
     * with such name is defined in the ComponentRegistry where this decorator is registered.
     * <p>
     * If multiple components for this type have been defined, and all are subject to decoration, consider using
     * {@link #forType(Class)}.
     *
     * @param type The class of the declared type of the components to decorate
     * @param name The name of the component to decorate
     * @param <C>  The declared type of the components to decorate
     * @return a builder for further configuration of this definition
     */
    static <C> PartialDecoratorDefinition<C> forTypeAndName(@Nonnull Class<C> type, @Nonnull String name) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(name, "The name must not be empty or null.");
        Assert.nonEmpty(name, "The name must not be empty or null.");
        return new PartialDecoratorDefinition<>() {
            @Override
            public <D extends C> DecoratorDefinition<C, D> with(@Nonnull ComponentDecorator<C, D> decorator) {
                Objects.requireNonNull(decorator, "decorator must not be null");
                return new DefaultDecoratorDefinition<>(i ->
                                                                Objects.equals(type, i.type())
                                                                        && Objects.equals(name, i.name()), decorator);
            }
        };
    }

    /**
     * Sets the{@code order} in which this decorator will be invoked on a Component relative to other decorators. The
     * relative order of decorators with the same {@code order} is undefined.
     * <p>
     * In absence of configuration, the order is {@code 0}.
     *
     * @param order The relative order in which to invoke this decorator.
     * @return this DecoratorDefinition fur fluent API.
     */
    DecoratorDefinition<C, D> order(int order);

    /**
     * Registers the given {@code handler} to be registered with the application's Lifecycle during startup. The handler
     * will be invoked in the given startup {@code phase} for each component that has been decorated.
     *
     * @param phase   The phase in which the handler must be invoked
     * @param handler The handler to invoke
     * @return this DecoratorDefinition fur fluent API.
     */
    DecoratorDefinition<C, D> onStart(int phase, ComponentLifecycleHandler<D> handler);

    /**
     * Registers the given {@code handler} to be registered with the application's Lifecycle during startup. The handler
     * will be invoked in the given startup {@code phase} for each component that has been decorated.
     *
     * @param phase   The phase in which the handler must be invoked
     * @param handler The handler to invoke
     * @return this DecoratorDefinition fur fluent API.
     */
    default DecoratorDefinition<C, D> onStart(int phase, Consumer<D> handler) {
        return onStart(phase, (cfg, cmp) -> {
            handler.accept(cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be registered with the application's Lifecycle during shutdown. The
     * handler will be invoked in the given startup {@code phase} for each component that has been decorated.
     *
     * @param phase   The phase in which the handler must be invoked
     * @param handler The handler to invoke
     * @return this DecoratorDefinition fur fluent API.
     */
    DecoratorDefinition<C, D> onShutdown(int phase, ComponentLifecycleHandler<D> handler);

    /**
     * Registers the given {@code handler} to be registered with the application's Lifecycle during shutdown. The
     * handler will be invoked in the given startup {@code phase} for each component that has been decorated.
     *
     * @param phase   The phase in which the handler must be invoked
     * @param handler The handler to invoke
     * @return this DecoratorDefinition fur fluent API.
     */
    default DecoratorDefinition<C, D> onShutdown(int phase, Consumer<D> handler) {
        return onShutdown(phase, (cfg, cmp) -> {
            handler.accept(cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Represents an intermediate phase in the creation of a {@link DecoratorDefinition}.
     *
     * @param <C> The declared type of the component to decorate.
     */
    interface PartialDecoratorDefinition<C> {

        /**
         * Defines the decorator function to use to decorate the target components.
         *
         * @param decorator The function to use to decorate a component.
         * @param <D>       The instance type of the decorated component.
         * @return a DecoratorDefinition for direct use or further configuration.
         */
        <D extends C> DecoratorDefinition<C, D> with(@Nonnull ComponentDecorator<C, D> decorator);
    }

    /**
     * Defines the behavior that all implementation of {@link DecoratorDefinition} must provide. This separation is used
     * to hide these methods from general use, as they are solely meant for Axon internals.
     *
     * @param <C> The declared type of the component.
     * @param <D> The implementation type of the decorator.
     */
    non-sealed interface CompletedDecoratorDefinition<C, D extends C> extends DecoratorDefinition<C, D> {

        /**
         * The order in which this decorator must be invoked. The relative order of decorators with the same
         * {@code order} is undefined.
         *
         * @return the order in which this decorator must be invoked.
         */
        int order();

        /**
         * Decorates the given {@code delegate} by returning the instance that should be used in its place. Generally,
         * this is a component that delegates calls to the {@code delegate}, although decorators may choose to return a
         * replacement altogether.
         *
         * @param delegate The component to decorate.
         * @return the decorated component.
         */
        Component<C> decorate(Component<C> delegate);

        /**
         * Indicates whether the component with given {@code componentId} matches the definition of this decorator.
         *
         * @param componentId The identifier of a component to possibly decorate.
         * @return {@code true} if the component's identifier matches this definition, otherwise {@code false}.
         */
        boolean matches(Component.Identifier<?> componentId);
    }
}