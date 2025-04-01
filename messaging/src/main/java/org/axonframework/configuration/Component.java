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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.Assert.assertThat;

/**
 * A wrapper of "components" used in the Axon Framework's configuration API.
 * <p>
 * A component describes an object that needs to be created, possibly based on other components in the
 * {@link NewConfiguration}.
 * <p>
 * Components are eagerly constructed when they are {@link #initLifecycle(NewConfiguration, LifecycleRegistry)
 * initialized} or lazily when {@link #resolve(NewConfiguration) resolved}. During
 * the initialization, they may trigger initialization of the components they depend on.
 *
 * @param <C> The type of component contained.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class Component<C> implements DescribableComponent {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Identifier<? extends C> identifier;
    private final ComponentFactory<? extends C> factory;
    private final BiConsumer<LifecycleRegistry, Supplier<? extends C>> lifecycleInitializer;
    private final Component<? extends C> dependent;
    private final AtomicBoolean lifecycleInitialized = new AtomicBoolean(false);
    private C instance;

    /**
     * Creates a {@code Component} for the given {@code identifier} created by the given {@code factory}. No lifecycle
     * handlers will be registered for this component.
     * <p>
     * If this component has a lifecycle, consider using {@link #Component(Identifier, ComponentFactory, BiConsumer)}
     * to register lifecycle handlers.
     *
     * @param identifier The identifier of the component.
     * @param factory    The factory building the component.
     */
    public Component(@Nonnull Identifier<? extends C> identifier, @Nonnull ComponentFactory<? extends C> factory) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        this.factory = requireNonNull(factory, "A Component factory cannot be null.");
        this.lifecycleInitializer = noop();
        this.dependent = null;
    }

    /**
     * Creates a {@code Component} for the given {@code identifier} created by the given {@code factory}. The given
     * {@code lifecycleInitializer} is invoked when the component it initialized, allowing registration of lifecycle
     * handlers.
     *
     * @param identifier           The identifier of the component.
     * @param factory              The factory building the component.
     * @param lifecycleInitializer The function to execute when the component is initialized
     * @param <D>                  The type of component the decorator returns
     */
    public <D extends C> Component(@Nonnull Identifier<C> identifier, @Nonnull ComponentFactory<D> factory,
                                   @Nonnull BiConsumer<LifecycleRegistry, Supplier<D>> lifecycleInitializer) {
        this.identifier = requireNonNull(identifier, "The given identifier cannot null.");
        this.factory = requireNonNull(factory, "A Component factory cannot be null.");
        requireNonNull(lifecycleInitializer, "A lifecycle initializer cannot be null.");
        //noinspection unchecked
        this.lifecycleInitializer = (lifecycleRegistry, supplier) -> lifecycleInitializer.accept(lifecycleRegistry,
                                                                                                 () -> (D) supplier.get());
        this.dependent = null;
    }

    private Component(@Nonnull Component<C> dependent, @Nonnull ComponentFactory<C> factory,
                      @Nonnull BiConsumer<LifecycleRegistry, Supplier<? extends C>> lifecycleInitializer) {
        this.dependent = dependent;
        this.identifier = dependent.identifier;
        this.factory = requireNonNull(factory, "A Component factory cannot be null.");
        this.lifecycleInitializer = lifecycleInitializer;
    }

    private static <C> BiConsumer<LifecycleRegistry, Supplier<? extends C>> noop() {
        return (lifecycleRegistry, supplier) -> {
        };
    }

    /**
     * Retrieves the object contained in this {@code Component}, triggering the {@link ComponentFactory factory} and all
     * attached {@link ComponentDecorator decorators} if the component hasn't been built yet.
     * <p>
     * This operation is {@code synchronized}, allowing the configuration to be thread-safe.
     * <p>
     * Upon initiation of the instance the
     * {@link LifecycleHandlerInspector#registerLifecycleHandlers(LifecycleRegistry, Object)} methods will be called to
     * resolve and register lifecycle methods.
     *
     * @param configuration     The configuration to retrieve other components from that
     *                          {@code this Component's ComponentFactory} may require during initialization.
     * @return The initialized component contained in this instance.
     */
    public synchronized C resolve(@Nonnull NewConfiguration configuration) {
        if (instance != null) {
            return instance;
        }
        requireNonNull(configuration, "The configuration cannot be null.");

        instance = factory.build(configuration);
        logger.debug("Instantiated component [{}]: {}", identifier, instance);

        return instance;
    }

    /**
     * Initialize this component by registering its lifecycle handlers with the given {@code lifecycleRegistry}. If the
     * component needs to be constructed, it can retrieve its dependencies (which may not have been initialized) from
     * given {@code configuration}.
     * <p>
     * This method does nothing if the component has already been initialized
     *
     * @param configuration     The configuration to retrieve dependencies from
     * @param lifecycleRegistry The registry in which to register handlers
     */
    public synchronized void initLifecycle(@Nonnull NewConfiguration configuration,
                                           @Nonnull LifecycleRegistry lifecycleRegistry) {
        Objects.requireNonNull(configuration, "The configuration cannot be null.");
        Objects.requireNonNull(lifecycleRegistry, "The lifecycle registry cannot be null.");

        if (!lifecycleInitialized.getAndSet(true)) {
            if (dependent != null) {
                dependent.initLifecycle(configuration, lifecycleRegistry);
            }
            lifecycleInitializer.accept(lifecycleRegistry, () -> resolve(configuration));
        }
    }

    /**
     * Checks if this {@code Component} is already initialized.
     *
     * @return {@code true} if this {@code Component} is initialized, {@code false} otherwise.
     */
    public synchronized boolean isInitialized() {
        return lifecycleInitialized.get();
    }

    /**
     * Indicated whether the instance for this component has already been resolved. If not, calling
     * {@link #resolve(NewConfiguration)} will do so.
     *
     * @return {@code true} if the instance for this component has already been resolved. Otherwise {@code false}.
     */
    public synchronized boolean isResolved() {
        return instance != null;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("identifier", identifier.toString());
        if (isResolved()) {
            descriptor.describeProperty("component", instance);
            descriptor.describeProperty("initialized", isInitialized());
            descriptor.describeProperty("resolved", true);
        } else {
            descriptor.describeProperty("factory", factory);
            descriptor.describeProperty("initialized", isInitialized());
            descriptor.describeProperty("resolved", false);
        }
    }

    /**
     * Returns a {@code Component} that decorates this component, calling given {@code decorator} to wrap (or replace)
     * the instance created by this {@code Component}.
     *
     * @param decorator the function that decorates the instance contained in this component
     * @return a new component that represents the decorated instance
     * @param <D> The type of component the decorator returns
     */
    public <D extends C> Component<C> decorate(ComponentDecorator<C, D> decorator) {
        return new Component<>(this, c -> decorator.decorate(c, identifier.name(), resolve(c)), noop());
    }

    /**
     * Returns a Component that decorates this component, calling given {@code decorator} to wrap (or replace) the
     * instance created by this Component.
     *
     * @param decorator            the function that decorates the instance contained in this component
     * @param lifecycleInitializer The function to execute when the component is initialized
     * @param <D>                  The type of component the decorator returns
     * @return a new component that represents the decorated instance
     */
    public <D extends C> Component<C> decorate(ComponentDecorator<C, D> decorator,
                                               BiConsumer<LifecycleRegistry, Supplier<D>> lifecycleInitializer) {
        //noinspection unchecked
        return new Component<>(this,
                               cf -> decorator.decorate(cf, identifier.name(), resolve(cf)),
                               (lifecycleRegistry, supplier) -> lifecycleInitializer.accept(lifecycleRegistry,
                                                                                            () -> (D) supplier.get())

        );
    }

    /**
     * Returns the unique {@link Identifier} of this {@code Component}.
     */
    public Identifier<? extends C> identifier() {
        return identifier;
    }

    /**
     * A tuple representing a {@code Component's} uniqueness, consisting out of a {@code type} and {@code name}.
     *
     * @param type The type of the component this object identifiers, typically an interface.
     * @param name The name of the component this object identifiers.
     * @param <C>  The type of the component this object identifiers, typically an interface.
     */
    public record Identifier<C>(@Nonnull Class<? extends C> type, @Nonnull String name) {

        /**
         * Compact constructor asserting whether the {@code type} and {@code name} are non-null and not empty.
         * @param type The type of the component
         * @param name The name of the component
         */
        public Identifier {
            requireNonNull(type, "The given type is unsupported because it is null.");
            assertThat(
                    requireNonNull(name, "The given name is unsupported because it is null."),
                    StringUtils::nonEmpty,
                    () -> new IllegalArgumentException("The given name is unsupported because it is empty.")
            );
        }

        @Override
        public String toString() {
            return type.getName() + ":" + name;
        }
    }
}
