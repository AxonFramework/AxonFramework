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
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.Assert.assertThat;

/**
 * A component used in the Axon {@link NewConfigurer}.
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
    private ComponentBuilder<C> builder;

    private final SortedMap<Integer, ComponentDecorator<C>> decorators = new TreeMap<>();
    // TODO Discuss what phase-enum we provide, and what the offset/default on the enum is for the default order.
    private int defaultDecoratorOrder = 0;

    private C instance;

    /**
     * Creates a {@code Component} for the given {@code config} with given {@code identifier} created by the given
     * {@code builder}.
     * <p>
     * When the {@link LifecycleSupportingConfiguration} is not initialized yet, consider using
     * {@link #Component(Identifier, Supplier, ComponentBuilder)} instead.
     *
     * @param identifier The identifier of the component.
     * @param config     The {@code NewConfiguration} the component is part of.
     * @param builder    The builder function of the component.
     */
    public Component(@Nonnull Identifier<C> identifier,
                     @Nonnull LifecycleSupportingConfiguration config,
                     @Nonnull ComponentBuilder<C> builder) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        requireNonNull(config, "The configuration supplier cannot be null.");
        this.configSupplier = () -> config;
        this.builder = requireNonNull(builder, "A Component builder cannot be null.");
    }

    /**
     * Creates a {@code Component} for the given {@code configSupplier} with given {@code identifier} created by the
     * given {@code builder}.
     *
     * @param identifier     The identifier of the component.
     * @param configSupplier The supplier function of the {@code NewConfiguration}.
     * @param builder        The builder function of the component.
     */
    public Component(@Nonnull Identifier<C> identifier,
                     @Nonnull Supplier<LifecycleSupportingConfiguration> configSupplier,
                     @Nonnull ComponentBuilder<C> builder) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        this.configSupplier = requireNonNull(configSupplier, "The configuration supplier cannot be null.");
        this.builder = requireNonNull(builder, "A Component builder cannot be null.");
    }

    /**
     * Retrieves the object contained in this {@code Component}, triggering the {@link ComponentBuilder builder} and all
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
        instance = builder.build(config);
        decorators.values()
                  .forEach(decorator -> instance = decorator.decorate(config, instance));
        logger.debug("Instantiated component [{}]: {}", identifier, instance);
        LifecycleHandlerInspector.registerLifecycleHandlers(config, instance);
        return instance;
    }

    /**
     * Updates the builder function for this {@code Component}.
     *
     * @param componentBuilder The new builder function for the {@code Component}.
     * @throws IllegalStateException When the component has already been retrieved using {@link #get()}.
     */
    public void update(@Nonnull ComponentBuilder<C> componentBuilder) {
        assertThat(
                this.instance,
                Objects::isNull,
                () -> new IllegalStateException(
                        "Cannot update component with [" + identifier + "] as it is already in use."
                )
        );
        this.builder = componentBuilder;
    }

    /**
     * Decorates the contained component upon {@link #get() initialization} by passing it through the given
     * {@code decorator}.
     *
     * @param decorator The {@code ComponentDecorator} to use on the contained component upon
     *                  {@link #get() initialization}.
     * @return This {@code Component}, for a fluent API.
     */
    public Component<C> decorate(@Nonnull ComponentDecorator<C> decorator) {
        decorators.put(++defaultDecoratorOrder, requireNonNull(decorator, "Component decorators cannot be null."));
        return this;
    }

    /**
     * Decorates the contained component upon {@link #get() initialization} by passing it through the given
     * {@code decorator} at the specified {@code order}.
     * <p>
     * The {@code order} of the {@code decorator} will impact the decoration ordering of the outcome of this component.
     * Will override previously registered {@link ComponentDecorator ComponentDecorators} if there already was one
     * present at the given {@code order}.
     *
     * @param order     Defines the ordering of the given {@code decorator} among all other
     *                  {@link ComponentDecorator ComponentDecorators} that have been registered.
     * @param decorator The {@code ComponentDecorator} to use on the contained component upon
     *                  {@link #get() initialization}.
     * @return This {@code Component}, for a fluent API.
     */
    public Component<C> decorate(int order,
                                 @Nonnull ComponentDecorator<C> decorator) {
        ComponentDecorator<C> previous =
                decorators.put(order, requireNonNull(decorator, "Component decorators cannot be null."));
        if (previous != null) {
            logger.warn("Replaced decorator [{}] at order [{}] with [{}].", previous, order, decorator);
        }
        return this;
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
     * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code type} and {@code name}.
     *
     * @param type The type of the component this object identifiers, typically an interface.
     * @param name The name of the component this object identifiers.
     * @param <C>  The type of the component this object identifiers, typically an interface.
     */
    public record Identifier<C>(@Nonnull Class<C> type, @Nonnull String name) {

        /**
         * Construct an {@code Component.Identifier} by setting the {@link #name()} to the {@link Class#getSimpleName()}
         * of the given {@code type}.
         *
         * @param type The type of the component this object identifiers, typically an interface.
         */
        public Identifier(@Nonnull Class<C> type) {
            this(type, requireNonNull(type, "The given type is unsupported because it is null.").getSimpleName());
        }

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
