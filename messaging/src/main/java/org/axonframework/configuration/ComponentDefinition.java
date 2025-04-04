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

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Defines the structure of a Component that is available in the {@link NewConfiguration} of the application or one of
 * its Modules.
 * <p>
 * Components are identified by a combination of their declared type and a name. The declared type is generally an
 * interface that all implementations are expected to implement. The name can be any non-empty string value that
 * identifies a particular instance of component. If the name is not relevant, for example because only a single
 * instance is expected to be present, it can be omitted in the definition, in which case it will default to the simple
 * class name of the declared Component type.
 * <p>
 * To create a component of type {@code MyComponentInterface} with an implementation that has a dependency, it would
 * look as follows:
 * <pre><code>
 * ComponentDefinition.ofType(MyComponentInterface.class)
 *                    .withFactory(cfg -> new MyComponentImplementation(cfg.getComponent(MyDependency.class)))
 *                    .onStart(0, MyComponentImplementation::start)
 *                    .onShutdown(0, MyComponentImplementation::shutdown
 * </code></pre>
 * <p>
 * Alternatively, you can use
 * <pre><code>
 *     ComponentDefinition.ofTypeAndName(MyComponentInterface.class, "MyName")
 *                        .withFactory(cfg -> ...)
 * </code></pre>
 * in case you need to distinguish between separate instances of the component.
 * <p>
 * If an instance of the component is already constructed, it is more efficient to register it directly, rather than
 * through a factory method. For example:
 * <pre><code>
 *     ComponentDefinition.ofTypeAndName(MyComponentInterface.class, "MyName")
 *                        .withInstance(myPreCreatedInstance)
 * </code></pre>
 *
 * @param <C> The declared type of the component
 */
public sealed interface ComponentDefinition<C> permits ComponentDefinition.ComponentCreator {

    /**
     * Starts defining a component with given declared {@code type}. The name will default to the simple class name of
     * that type. To distinguish between different instances of the same type, consider using
     * {@link #ofTypeAndName(Class, String)} instead.
     * <p>
     * Either {@link IncompleteComponentDefinition#withFactory(ComponentFactory) withFactory(...)} or
     * {@link IncompleteComponentDefinition#withInstance(Object) withInstance(...)} must be called on the result of this
     * invocation to create a valid {@code ComponentDefinition} instance.
     *
     * @param type The declared type of the component
     * @param <C>  The declared type of the component
     * @return a builder to complete the creation of a ComponentDefinition
     * @see #ofTypeAndName(Class, String)
     */
    static <C> IncompleteComponentDefinition<C> ofType(Class<C> type) {
        return ofTypeAndName(type, type.getSimpleName());
    }

    /**
     * Starts defining a component with given declared {@code type} and {@code name}. If only a single instance of a
     * component is expected to be used, consider using {@link #ofType(Class)} instead.
     *
     * @param type The declared type of this component
     * @param name The name of this component
     * @param <C>  The declared type of this component
     * @return a builder to complete the creation of a ComponentDefinition
     */
    static <C> IncompleteComponentDefinition<C> ofTypeAndName(Class<C> type, String name) {
        return new IncompleteComponentDefinition<>() {

            @Override
            public ComponentDefinition<C> withInstance(C instance) {
                return new InstantiatedComponentDefinition<>(new Component.Identifier<>(type, name), instance);
            }

            @Override
            public ComponentDefinition<C> withFactory(ComponentFactory<? extends C> factory) {
                return new LazyInitializedComponentDefinition<>(new Component.Identifier<>(type, name), factory);
            }
        };
    }

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    ComponentDefinition<C> onStart(int phase, ComponentLifecycleHandler<C> handler);

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    default ComponentDefinition<C> onStart(int phase, Consumer<C> handler) {
        return onStart(phase, (cfg, cmp) -> {
            handler.accept(cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the startup lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    default ComponentDefinition<C> onStart(int phase, BiConsumer<NewConfiguration, C> handler) {
        return onStart(phase, (cfg, cmp) -> {
            handler.accept(cfg, cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    ComponentDefinition<C> onShutdown(int phase, ComponentLifecycleHandler<C> handler);

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    default ComponentDefinition<C> onShutdown(int phase, Consumer<C> handler) {
        return onShutdown(phase, (cfg, cmp) -> {
            handler.accept(cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Registers the given {@code handler} to be invoked during the shutdown lifecycle of the application in the given
     * {@code phase}.
     *
     * @param phase   The phase in which to invoke this handler
     * @param handler The action to execute on the component or
     * @return a ComponentDefinition with the start handler defined
     */
    default ComponentDefinition<C> onShutdown(int phase, BiConsumer<NewConfiguration, C> handler) {
        return onShutdown(phase, (cfg, cmp) -> {
            handler.accept(cfg, cmp);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Mandatory interface to be implemented by all implementations of {@code ComponentDefinition}. This separation will
     * hide these methods from general use of the ComponentDefinition, while enforcing that all ComponentDefinition
     * instances will declare this method.
     *
     * @param <C> The type of component defined
     */
    non-sealed interface ComponentCreator<C> extends ComponentDefinition<C> {

        /**
         * Create a Component matching the requirements configured on this definition. Multiple invocations of this
         * method should return the same instance.
         *
         * @return a component based on this definition
         */
        Component<C> createComponent();
    }

    /**
     * Represents an intermediate step in the creation of a {@link ComponentDefinition}. This step requires the call of
     * either {@link #withInstance(Object) withInstance(...)} or {@link #withFactory(ComponentFactory) withFactory(...)}
     * to create a usable ComponentDefinition.
     *
     * @param <C> The declared type of the component
     */
    interface IncompleteComponentDefinition<C> {

        /**
         * Creates a ComponentDefinition with the given pre-instantiated {@code instance} of a component.
         * <p>
         * If you require lazy instantiation of components, consider using {@link #withFactory(ComponentFactory)}
         * instead.
         *
         * @param instance The instance to declare as the implementation of this component
         * @return a ComponentDefinition for further configuration
         */
        ComponentDefinition<C> withInstance(C instance);

        /**
         * Creates a ComponentDefinition that creates an instance on-demand using the given {@code factory} method.
         * <p>
         * If you have already instantiated a component, consider using {@link #withInstance(Object)} instead.
         *
         * @param factory The factory used to create an instance, when required
         * @return a ComponentDefinition for further configuration
         */
        ComponentDefinition<C> withFactory(ComponentFactory<? extends C> factory);
    }
}
