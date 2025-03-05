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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.Component.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract implementation of the {@link NewConfigurer} allowing for reuse of {@link Component},
 * {@link ComponentDecorator}, and {@link Module} registration for the {@link RootConfigurer} and {@link Module}
 * implementations alike.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class AbstractConfigurer<S extends NewConfigurer<S>> implements NewConfigurer<S> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final Components components = new Components();
    private final List<ConfigurerEnhancer> enhancers = new ArrayList<>();
    private final List<Module<?>> modules = new ArrayList<>();
    private final List<NewConfiguration> moduleConfigurations = new ArrayList<>();

    protected final LifecycleSupportingConfiguration config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize the {@code AbstractConfigurer} based on the given {@code config}.
     *
     * @param config The life cycle supporting configuration used as the <b>parent</b> configuration of the
     *               {@link LocalConfiguration}.
     */
    protected AbstractConfigurer(@Nullable LifecycleSupportingConfiguration config) {
        this.config = new LocalConfiguration(config);
    }

    @Override
    public <C> S registerComponent(@Nonnull Class<C> type,
                                   @Nonnull String name,
                                   @Nonnull ComponentBuilder<C> builder) {
        logger.debug("Registering component [{}] of type [{}].", name, type);
        Identifier<C> identifier = new Identifier<>(type, name);
        Component<C> previous = components.put(identifier, new Component<>(identifier, config, builder));
        if (previous != null) {
            logger.warn("Replaced a previous Component registered under type [{}] and name [{}].", name, type);
        }
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public <C> S registerDecorator(@Nonnull Class<C> type,
                                   @Nonnull String name,
                                   int order,
                                   @Nonnull ComponentDecorator<C> decorator) {
        logger.debug("Registering decorator for [{}] of type [{}] at order #{}.", name, type, order);
        Identifier<C> identifier = new Identifier<>(type, name);
        logger.debug("Registering decorator for [{}] at order #{}.", identifier, order);
        components.getOptionalComponent(identifier)
                  .map(component -> component.decorate(decorator, order))
                  .orElseThrow(() -> new IllegalArgumentException(
                          "Cannot decorate type [" + identifier + "] since there is no component builder for this type."
                  ));
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public S registerEnhancer(@Nonnull ConfigurerEnhancer enhancer) {
        logger.debug("Registering enhancer [{}].", enhancer.getClass().getSimpleName());
        this.enhancers.add(enhancer);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public <M extends Module<M>> S registerModule(@Nonnull ModuleBuilder<M> builder) {
        Module<?> module = builder.build(config());
        logger.debug("Registering module [{}].", module.getClass().getSimpleName());
        this.modules.add(module);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public boolean hasComponent(@Nonnull Class<?> type,
                                @Nonnull String name) {
        return components.contains(new Component.Identifier<>(type, name));
    }

    @Override
    public <C extends NewConfiguration> C build() {
        if (!initialized.getAndSet(true)) {
            invokeEnhancers();
            buildModules();
        }
        //noinspection unchecked
        return (C) config;
    }

    /**
     * Invoke all the {@link #registerEnhancer(ConfigurerEnhancer) registered} {@link ConfigurerEnhancer enhancers} on
     * this {@code Configurer} implementation in their {@link ConfigurerEnhancer#order()}. This will ensure all sensible
     * default components and decorators are in place from these enhancers.
     */
    private void invokeEnhancers() {
        enhancers.stream()
                 .sorted(Comparator.comparingInt(ConfigurerEnhancer::order))
                 .forEach(enhancer -> enhancer.enhance(this));
    }

    /**
     * Ensure all registered {@link Module Modules} are built too. Store their {@link NewConfiguration} results for
     * exposure on {@link NewConfiguration#getModuleConfigurations()}.
     */
    private void buildModules() {
        for (Module<?> module : modules) {
            moduleConfigurations.add(module.build());
        }
    }

    @Override
    public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        throw new UnsupportedOperationException("Registering start handlers is not supported on this module.");
    }

    @Override
    public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        throw new UnsupportedOperationException("Registering shutdown handlers is not supported on this module.");
    }

    /**
     * Returns the {@link LifecycleSupportingConfiguration} of this {@link NewConfigurer} implementation.
     *
     * @return The {@link LifecycleSupportingConfiguration} of this {@link NewConfigurer} implementation.
     */
    protected LifecycleSupportingConfiguration config() {
        return config;
    }

    /**
     * A {@link LifecycleSupportingConfiguration} implementation acting as the local configuration of this configurer.
     * Can be implemented by {@link AbstractConfigurer} implementation that need desire to reuse the access logic for
     * {@link Component Components} and {@link Module Modules} as provided by this implementation.
     */
    public class LocalConfiguration implements LifecycleSupportingConfiguration {

        private final LifecycleSupportingConfiguration parent;

        /**
         * Construct a {@code LocalConfiguration} using the given {@code parent} configuration.
         * <p>
         * If this configuration does not have a certain {@link Component}, it will fall back to it's {@code parent}.
         * <p>
         * Note that the {@code parent} can {@code null}.
         *
         * @param parent The parent life cycle supporting configuration to fall back on when necessary.
         */
        public LocalConfiguration(@Nullable LifecycleSupportingConfiguration parent) {
            this.parent = parent;
        }

        @Override
        public void onStart(int phase, @Nonnull LifecycleHandler startHandler) {
            if (parent != null) {
                parent.onStart(phase, startHandler);
            } else {
                AbstractConfigurer.this.onStart(phase, startHandler);
            }
        }

        @Override
        public void onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
            if (parent != null) {
                parent.onShutdown(phase, shutdownHandler);
            } else {
                AbstractConfigurer.this.onShutdown(phase, shutdownHandler);
            }
        }

        @Nonnull
        @Override
        public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type,
                                                    @Nonnull String name) {
            return components.getOptional(new Identifier<>(type, name))
                             .or(() -> optionalFromParent(type, name, () -> null));
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nonnull String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            Identifier<C> identifier = new Identifier<>(type, name);
            Object component = components.computeIfAbsent(
                    identifier,
                    id -> new Component<>(identifier, config(),
                                          c -> optionalFromParent(type, name, defaultImpl).orElseGet(defaultImpl))
            ).get();
            return identifier.type().cast(component);
        }

        private <C> Optional<C> optionalFromParent(Class<C> type, String name, Supplier<C> defaultSupplier) {
            return parent != null
                    ? parent.getOptionalComponent(type, name).or(() -> Optional.ofNullable(defaultSupplier.get()))
                    : Optional.ofNullable(defaultSupplier.get());
        }

        @Override
        public List<NewConfiguration> getModuleConfigurations() {
            return List.copyOf(moduleConfigurations);
        }
    }
}
