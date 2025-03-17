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
import org.axonframework.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.Assert.assertThat;

/**
 * A component used in the Axon's configuration API.
 * <p>
 * A component describes an object that needs to be created, possibly based on other components in the
 * {@link LifecycleSupportingConfiguration}, and initialized as part of the {@code NewConfiguration}.
 * <p>
 * Components are lazily initialized when they are accessed. During the initialization, they may trigger initialization
 * of components they depend on.
 *
 * @param <C> The type of component contained.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class Component<C> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Identifier<C> identifier;
    private final Supplier<LifecycleSupportingConfiguration> configSupplier;
    private final ComponentFactory<C> factory;

    private C instance;

    /**
     * Creates a {@code Component} for the given {@code config} with given {@code identifier} created by the given
     * {@code factory}.
     * <p>
     * When the {@link LifecycleSupportingConfiguration} is not initialized yet, consider using
     * {@link #Component(Identifier, Supplier, ComponentFactory)} instead.
     *
     * @param identifier The identifier of the component.
     * @param config     The {@code NewConfiguration} the component is part of.
     * @param factory    The factory building the component.
     */
    public Component(@Nonnull Identifier<C> identifier,
                     @Nonnull LifecycleSupportingConfiguration config,
                     @Nonnull ComponentFactory<C> factory) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        requireNonNull(config, "The configuration supplier cannot be null.");
        this.configSupplier = () -> config;
        this.factory = requireNonNull(factory, "A Component factory cannot be null.");
    }

    /**
     * Creates a {@code Component} for the given {@code configSupplier} with given {@code identifier} created by the
     * given {@code factory}.
     *
     * @param identifier     The identifier of the component.
     * @param configSupplier The supplier function of the {@code NewConfiguration}.
     * @param factory        The factory building the component.
     */
    public Component(@Nonnull Identifier<C> identifier,
                     @Nonnull Supplier<LifecycleSupportingConfiguration> configSupplier,
                     @Nonnull ComponentFactory<C> factory) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        this.configSupplier = requireNonNull(configSupplier, "The configuration supplier cannot be null.");
        this.factory = requireNonNull(factory, "A Component factory cannot be null.");
    }

    /**
     * Retrieves the object contained in this {@code Component}, triggering the {@link ComponentFactory factory} and all
     * attached {@link ComponentDecorator decorators} if the component hasn't been built yet.
     * <p>
     * Upon initiation of the instance the
     * {@link LifecycleHandlerInspector#registerLifecycleHandlers(LifecycleSupportingConfiguration, Object)} methods
     * will be called to resolve and register lifecycle methods.
     *
     * @return The initialized component contained in this instance.
     */
    public C get() {
        if (instance != null) {
            return instance;
        }

        LifecycleSupportingConfiguration config = configSupplier.get();
        instance = factory.build(config);
        logger.debug("Instantiated component [{}]: {}", identifier, instance);
        LifecycleHandlerInspector.registerLifecycleHandlers(config, instance);
        return instance;
    }

    /**
     * Checks if this {@code Component} is already initialized.
     *
     * @return {@code true} if this {@code Component} is initialized, {@code false} otherwise.
     */
    public boolean isInitialized() {
        return instance != null;
    }

    /**
     * Returns a Component that decorates this component, calling given {@code decorator} to wrap (or replace) the
     * instance created by this Component.
     *
     * @param decorator the function that decorates the instance contained in this component
     * @return a new component that represents the decorated instance
     */
    public Component<C> decorate(ComponentDecorator<C> decorator) {
        return new Component<>(identifier, configSupplier, c -> decorator.decorate(c, identifier.name(), get()));
    }

    /**
     * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code type} and {@code name}.
     *
     * @param type The type of the component this object identifiers, typically an interface.
     * @param name The name of the component this object identifiers.
     * @param <C>  The type of the component this object identifiers, typically an interface.
     */
    public record Identifier<C>(@Nonnull Class<C> type, @Nonnull String name) {

        /**
         * Compact constructor asserting whether the {@code type} and {@code name} are non-null and not empty.
         */
        public Identifier {
            requireNonNull(type, "The given type is unsupported because it is null.");
            assertThat(
                    requireNonNull(name, "The given name is unsupported because it is null."),
                    StringUtils::nonEmpty,
                    () -> new IllegalArgumentException("The given name is unsupported because it is empty.")
            );
        }
    }
}
