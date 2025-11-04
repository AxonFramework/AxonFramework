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
import jakarta.annotation.Nullable;
import org.axonframework.common.TypeReference;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Defines the structure of a {@link Component} that is available in the {@link Configuration} of the application or one
 * of its {@link Module Modules}.
 * <p>
 * Components are identified by a combination of their declared type and a name. The declared type is generally an
 * interface that all implementations are expected to implement. The name can be any non-empty string value that
 * identifies a particular instance of a component. If the name is not relevant, for example because only a single
 * instance is expected to be present, it can be omitted in the definition, in which case it will default to the simple
 * class name of the declared {@code Component} type.
 * <p>
 * For example, to create a component of type {@code MyComponentInterface} with an implementation that has a dependency,
 * it would look as follows:
 * <pre><code>
 * ComponentDefinition.ofType(MyComponentInterface.class)
 *                    .withBuilder(config -> new MyComponentImplementation(config.getComponent(MyDependency.class)))
 *                    .onStart(0, MyComponentImplementation::start)
 *                    .onShutdown(0, MyComponentImplementation::shutdown)
 * </code></pre>
 * <p>
 * Alternatively, you can specify a name to make multiple instances of the same component in the same configuration:
 * <pre><code>
 *     ComponentDefinition.ofTypeAndName(MyComponentInterface.class, "MyName")
 *                        .withBuilder(config -> ...)
 * </code></pre>
 * <p>
 * If an instance of the component is already constructed, it is more efficient to register it directly
 * <pre><code>
 *     ComponentDefinition.ofTypeAndName(MyComponentInterface.class, "MyName")
 *                        .withInstance(myPreCreatedInstance)
 * </code></pre>
 *
 * @param <C> The declared type of the component.
 * @author Allard Buijze
 * @since 5.0.0
 */
public sealed interface ComponentDefinition<C> permits ComponentDefinition.ComponentCreator {

    /**
     * Starts defining a component with given declared {@code type}. To distinguish between different instances of the
     * same type, consider using {@link #ofTypeAndName(Class, String)} instead. In case the component carries a generic
     * type, consider using {@link #ofType(TypeReference)} instead to prevent casting errors during registration of the
     * component.
     * <p>
     * Either {@link IncompleteComponentDefinition#withBuilder(ComponentBuilder) withBuilder(...)} or
     * {@link IncompleteComponentDefinition#withInstance(Object) withInstance(...)} must be called on the result of this
     * invocation to create a valid {@code ComponentDefinition} instance.
     *
     * @param type The declared type of the component.
     * @param <C>  The declared type of the component.
     * @return A builder to complete the creation of a {@code ComponentDefinition}.
     * @see #ofTypeAndName(Class, String)
     */
    static <C> IncompleteComponentDefinition<C> ofType(@Nonnull Class<C> type) {
        return ofTypeAndName(type, null);
    }

    /**
     * Starts defining a component with given declared {@code type} and {@code name}. If only a single instance of a
     * component is expected to be used, consider using {@link #ofType(Class)} instead. In case the component carries a
     * generic type, consider using {@link #ofTypeAndName(TypeReference, String)} instead to prevent casting errors
     * during registration of the component.
     *
     * @param type The declared type of this component.
     * @param name The name of this component. Use {@code null} when there is no name or use {@link #ofType(Class)}
     *             instead.
     * @param <C>  The declared type of this component.
     * @return A builder to complete the creation of a {@code ComponentDefinition}.
     */
    static <C> IncompleteComponentDefinition<C> ofTypeAndName(@Nonnull Class<C> type, @Nullable String name) {
        return new IncompleteComponentDefinition<>() {

            @Override
            public ComponentDefinition<C> withInstance(@Nonnull C instance) {
                return new InstantiatedComponentDefinition<>(new Component.Identifier<>(type, name), instance);
            }

            @Override
            public ComponentDefinition<C> withBuilder(@Nonnull ComponentBuilder<? extends C> builder) {
                return new LazyInitializedComponentDefinition<>(new Component.Identifier<>(type, name), builder);
            }
        };
    }

    /**
     * Starts defining a component with given declared {@code type}. To distinguish between different instances of the
     * same type, consider using {@link #ofTypeAndName(TypeReference, String)} instead.
     * <p>
     * This method is a convenience overload of {@link #ofTypeAndName(Class, String)} that can accept a type reference
     * so components with generic types can be registered without casting errors. If your component does not have a
     * generic type, consider using {@link #ofTypeAndName(Class, String)} instead.
     * <p>
     * Either {@link IncompleteComponentDefinition#withBuilder(ComponentBuilder) withBuilder(...)} or
     * {@link IncompleteComponentDefinition#withInstance(Object) withInstance(...)} must be called on the result of this
     * invocation to create a valid {@code ComponentDefinition} instance.
     *
     * @param type The declared type of the component.
     * @param <C>  The declared type of the component.
     * @return A builder to complete the creation of a {@code ComponentDefinition}.
     * @see #ofTypeAndName(Class, String)
     */
    static <C> IncompleteComponentDefinition<C> ofType(@Nonnull TypeReference<C> type) {
        return ofTypeAndName(type, null);
    }

    /**
     * Starts defining a component with given declared {@code type} and {@code name}. If only a single instance of a
     * component is expected to be used, consider using {@link #ofType(TypeReference)} instead.
     * <p>
     * This method is a convenience overload of {@link #ofTypeAndName(Class, String)} that can accept a type reference
     * so components with generic types can be registered without casting errors. If your component does not have a
     * generic type, consider using {@link #ofTypeAndName(Class, String)} instead.
     *
     * @param type The declared type of this component.
     * @param name The name of this component.
     * @param <C>  The declared type of this component.
     * @return A builder to complete the creation of a {@code ComponentDefinition}.
     */
    static <C> IncompleteComponentDefinition<C> ofTypeAndName(@Nonnull TypeReference<C> type, @Nullable String name) {
        return new IncompleteComponentDefinition<>() {
            private final Component.Identifier<C> identifier = new Component.Identifier<>(type, name);

            @Override
            public ComponentDefinition<C> withInstance(@Nonnull C instance) {
                return new InstantiatedComponentDefinition<>(identifier, instance);
            }

            @Override
            public ComponentDefinition<C> withBuilder(@Nonnull ComponentBuilder<? extends C> builder) {
                return new LazyInitializedComponentDefinition<>(identifier, builder);
            }
        };
    }

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke the given {@code handler}.
     * @param handler The start handler to execute on the component.
     * @return A {@code ComponentDefinition} with the start handler defined.
     */
    ComponentDefinition<C> onStart(int phase, @Nonnull ComponentLifecycleHandler<C> handler);

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke the given {@code handler}.
     * @param handler The start handler to execute on the component.
     * @return A {@code ComponentDefinition} with the start handler defined.
     */
    default ComponentDefinition<C> onStart(int phase, @Nonnull Consumer<C> handler) {
        return onStart(phase, (config, component) -> {
            Objects.requireNonNull(handler, "The start handler cannot be null.")
                   .accept(component);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke the given {@code handler}.
     * @param handler The start handler to execute on the component.
     * @return A {@code ComponentDefinition} with the start handler defined.
     */
    default ComponentDefinition<C> onStart(int phase, @Nonnull BiConsumer<Configuration, C> handler) {
        return onStart(phase, (config, component) -> {
            Objects.requireNonNull(handler, "The start handler cannot be null.")
                   .accept(config, component);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke the given {@code handler}.
     * @param handler The action to execute on the component.
     * @return A {@code ComponentDefinition} with the shutdown handler defined.
     */
    ComponentDefinition<C> onShutdown(int phase, @Nonnull ComponentLifecycleHandler<C> handler);

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke the given {@code handler}.
     * @param handler The action to execute on the component.
     * @return A {@code ComponentDefinition} with the shutdown handler defined.
     */
    default ComponentDefinition<C> onShutdown(int phase, @Nonnull Consumer<C> handler) {
        return onShutdown(phase, (config, component) -> {
            Objects.requireNonNull(handler, "The shutdown handler cannot be null.")
                   .accept(component);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler.
     * @param handler The action to execute on the component.
     * @return A {@code ComponentDefinition} with the shutdown handler defined.
     */
    default ComponentDefinition<C> onShutdown(int phase, @Nonnull BiConsumer<Configuration, C> handler) {
        return onShutdown(phase, (config, component) -> {
            Objects.requireNonNull(handler, "The shutdown handler cannot be null.")
                   .accept(config, component);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Returns the given {@link Component.Identifier#typeAsClass() type as a Class} of this {@code ComponentDefinition},
     * set on {@link #ofType(Class)} or {@link #ofTypeAndName(Class, String)}.
     *
     * @return The given {@link Component.Identifier#typeAsClass() type as a Class} of this {@code ComponentDefinition}.
     */
    Class<C> rawType();

    /**
     * Returns the given name of this {@code ComponentDefinition}, set on {@link #ofTypeAndName(Class, String)}.
     *
     * @return The given name of this {@code ComponentDefinition}.
     */
    @Nullable
    String name();

    /**
     * Mandatory interface to be implemented by all implementations of {@code ComponentDefinition}.
     * <p>
     * This separation will hide these methods from general use of the {@code ComponentDefinition}, while enforcing that
     * all {@code ComponentDefinition} instances will declare this method.
     *
     * @param <C> The type of component defined.
     */
    non-sealed interface ComponentCreator<C> extends ComponentDefinition<C> {

        /**
         * Create a component matching the requirements configured on this definition.
         * <p>
         * Multiple invocations of this method should return the same instance.
         *
         * @return A component based on this definition.
         */
        Component<C> createComponent();
    }

    /**
     * Represents an intermediate step in the creation of a {@link ComponentDefinition}.
     * <p>
     * This step requires the call of either {@link #withInstance(Object) withInstance(...)} or
     * {@link #withBuilder(ComponentBuilder) withBuilder(...)} to create a usable {@code ComponentDefinition}.
     *
     * @param <C> The declared type of the component.
     */
    interface IncompleteComponentDefinition<C> {

        /**
         * Creates a {@code ComponentDefinition} with the given pre-instantiated {@code instance} of a component.
         * <p>
         * If you require lazy instantiation of components, consider using {@link #withBuilder(ComponentBuilder)}
         * instead.
         *
         * @param instance The instance to declare as the implementation of this component.
         * @return A {@code ComponentDefinition} for further configuration.
         */
        ComponentDefinition<C> withInstance(@Nonnull C instance);

        /**
         * Creates a {@code ComponentDefinition} that creates an instance on-demand using the given {@code builder}
         * method.
         * <p>
         * If you have already instantiated a component, consider using {@link #withInstance(Object)} instead.
         *
         * @param builder The builder used to create an instance, when required.
         * @return A {@code ComponentDefinition} for further configuration.
         */
        ComponentDefinition<C> withBuilder(@Nonnull ComponentBuilder<? extends C> builder);
    }
}
