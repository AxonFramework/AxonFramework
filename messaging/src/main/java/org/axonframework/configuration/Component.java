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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A Component used in the Axon Configurer. A Component describes an object that needs to be created, possibly based on
 * other components in the configuration, and initialized as part of a NewConfiguration. Components are lazily
 * initialized when they are accessed. During the initialization, they may trigger initialization of components they
 * depend on.
 *
 * @param <C> The type of Component contained
 * @author Allard Buijze
 * @since 3.0
 */
public class Component<C> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Supplier<NewConfiguration> configSupplier;
    private final String name;
    private ComponentBuilder<C> builder;
    private final SortedMap<Integer, ComponentDecorator<C>> decorators = new TreeMap<>();

    private C instance;

    /**
     * Creates a component for the given {@code config} with given {@code name} created by the given
     * {@code builderFunction}. Then the NewConfiguration is not initialized yet, consider using
     * {@link #Component(Supplier, String, ComponentBuilder)} instead.
     *
     * @param config  The NewConfiguration the component is part of
     * @param name    The name of the component
     * @param builder The builder function of the component
     */
    public Component(@Nonnull NewConfiguration config,
                     @Nonnull String name,
                     @Nonnull ComponentBuilder<C> builder) {
        this(() -> config, name, builder);
    }

    /**
     * Creates a component for the given {@code config} with given {@code name} created by the given
     * {@code builderFunction}.
     *
     * @param config  The supplier function of the configuration
     * @param name    The name of the component
     * @param builder The builder function of the component
     */
    public Component(@Nonnull Supplier<NewConfiguration> config,
                     @Nonnull String name,
                     @Nonnull ComponentBuilder<C> builder) {
        this.configSupplier = requireNonNull(config, "The configuration supplier cannot be null.");
        this.name = name;
        this.builder = requireNonNull(builder, "A Component builder cannot be null.");
    }

    /**
     * Retrieves the object contained in this component, triggering the builder function if the component hasn't been
     * built yet. Upon initiation of the instance the
     * {@link LifecycleHandlerInspector#registerLifecycleHandlers(NewConfiguration, Object)} methods will be called to
     * resolve and register lifecycle methods.
     *
     * @return the initialized component contained in this instance
     */
    public C get() {
        if (instance == null) {
            NewConfiguration config = configSupplier.get();
            instance = builder.build(config);
            decorators.values()
                      .forEach(decorator -> instance = decorator.decorate(config, instance));
            logger.debug("Instantiated component [{}]: {}", name, instance);
            LifecycleHandlerInspector.registerLifecycleHandlers(config, instance);
        }
        return instance;
    }

    /**
     * Updates the builder function for this component.
     *
     * @param componentBuilder The new builder function for the component
     * @throws IllegalStateException when the component has already been retrieved using {@link #get()}.
     */
    public void update(@Nonnull ComponentBuilder<C> componentBuilder) {
        Assert.state(instance == null, () -> "Cannot update [" + name + "] as it is already in use.");
        this.builder = componentBuilder;
    }

    /**
     * @param decorator
     * @return
     */
    public Component<C> decorate(@Nonnull ComponentDecorator<C> decorator) {
        // TODO this will hit Integer ceiling, so think of something else.
        decorators.put(decorators.lastKey() + 1, requireNonNull(decorator, "Component decorators cannot be null."));
        return this;
    }

    /**
     * @param order
     * @param decorator
     * @return
     */
    public Component<C> decorate(Integer order,
                                 @Nonnull ComponentDecorator<C> decorator) {
        ComponentDecorator<C> previous =
                decorators.put(order, requireNonNull(decorator, "Component decorators cannot be null."));
        if (previous != null) {
            logger.warn("Replaced decorator [{}] at order [{}] with [{}].", previous, order, decorator);
        }
        return this;
    }

    /**
     * Checks if the component is already initialized.
     *
     * @return true if component is initialized
     */
    public boolean isInitialized() {
        return instance != null;
    }
}
