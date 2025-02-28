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
import jakarta.annotation.Nullable;
import org.axonframework.configuration.Component.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
    protected final List<Module<?>> modules = new ArrayList<>();
    protected final List<NewConfiguration> moduleConfigurations = new ArrayList<>();

    protected final LifecycleSupportingConfiguration config;

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
        components.put(identifier, new Component<>(identifier, config, builder));
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
    public <M extends Module<M>> S registerModule(@Nonnull ModuleBuilder<M> builder) {
        Module<?> module = builder.build(config());
        logger.debug("Registering module [{}].", module.getClass().getSimpleName());
        this.modules.add(module);
        //noinspection unchecked
        return (S) this;
    }

    @Override
    public <C extends NewConfiguration> C build() {
        // Ensure all registered modules are built too.
        for (Module<?> module : modules) {
            moduleConfigurations.add(module.build());
        }
        //noinspection unchecked
        return (C) config;
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
                             .or(() -> moduleConfigurations.stream()
                                                           .map(c -> c.getOptionalComponent(type, name))
                                                           .findFirst()
                                                           .orElse(Optional.empty()))
                             .or(() -> parent != null ? parent.getOptionalComponent(type, name) : Optional.empty());
        }

        @Nonnull
        @Override
        public <C> C getComponent(@Nonnull Class<C> type,
                                  @Nonnull String name,
                                  @Nonnull Supplier<C> defaultImpl) {
            // TODO make this go down the chain through the parent somehow
            Identifier<C> identifier = new Identifier<>(type, name);
            Stream<Module<?>> moduleStream = modules.stream().filter(module -> true);
            Object component = components.computeIfAbsent(
                    identifier,
                    t -> new Component<>(identifier, config(),
                                         c -> c.getOptionalComponent(type, name).orElseGet(defaultImpl))
            ).get();
            return identifier.type().cast(component);
        }

        @Override
        public List<Module<?>> getModules() {
            List<Module<?>> modules = new ArrayList<>(AbstractConfigurer.this.modules);
            moduleConfigurations.forEach(moduleConfig -> modules.addAll(moduleConfig.getModules()));
            return modules;
        }
    }
}
