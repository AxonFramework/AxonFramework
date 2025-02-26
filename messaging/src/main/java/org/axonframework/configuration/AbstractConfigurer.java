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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract implementation of the {@link NewConfigurer} allowing for reuse of {@link Component},
 * {@link ComponentDecorator}, and {@link Module} registration for the {@link RootConfigurer} and {@link Module}
 * implementations alike.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class AbstractConfigurer implements NewConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final Components components = new Components();
    protected final List<Module> modules = new ArrayList<>();
    protected final LifecycleSupportingConfiguration config;

    /**
     * Initialize the Configurer.
     */
    protected AbstractConfigurer(@Nullable LifecycleSupportingConfiguration config) {
        this.config = new ConfigurationImpl(config);
    }

    @Override
    public <C> NewConfigurer registerComponent(@Nonnull Class<C> type,
                                               @Nonnull ComponentBuilder<C> builder) {
        logger.debug("Registering component [{}].", type.getSimpleName());
        components.put(type, new Component<>(type.getSimpleName(), config, builder));
        return this;
    }

    @Override
    public <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                               @Nonnull ComponentDecorator<C> decorator) {
        String name = type.getSimpleName();
        logger.debug("Registering decorator for [{}].", name);
        components.getOptionalComponent(type)
                  .map(component -> component.decorate(decorator))
                  .orElseThrow(() -> new IllegalArgumentException(
                          "Cannot decorate type [" + type + "] since there is no component builder for this type."
                  ));
        return this;
    }

    @Override
    public <C> NewConfigurer registerDecorator(@Nonnull Class<C> type,
                                               int order,
                                               @Nonnull ComponentDecorator<C> decorator) {
        String name = type.getSimpleName();
        logger.debug("Registering decorator for [{}] at order #{}.", name, order);
        components.getOptionalComponent(type)
                  .map(component -> component.decorate(order, decorator))
                  .orElseThrow(() -> new IllegalArgumentException(
                          "Cannot decorate type [" + type + "] since there is no component builder for this type."
                  ));
        return this;
    }

    @Override
    public NewConfigurer registerModule(@Nonnull ModuleBuilder moduleBuilder) {
        Module module = moduleBuilder.build(config());
        logger.debug("Registering module [{}].", module.getClass().getSimpleName());
        this.modules.add(module);
        return this;
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

    private class ConfigurationImpl implements LifecycleSupportingConfiguration {

        private final LifecycleSupportingConfiguration parent;

        public ConfigurationImpl(@Nullable LifecycleSupportingConfiguration parent) {
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

        @Override
        public <T> Optional<T> getOptionalComponent(@Nonnull Class<T> type) {
            // TODO cycle through modules here too
            return components.getOptional(type)
                             .or(() -> parent != null ? parent.getOptionalComponent(type) : Optional.empty());
        }

        @Override
        @Nonnull
        public <T> T getComponent(@Nonnull Class<T> type,
                                  @Nonnull Supplier<T> defaultImpl) {
            // TODO make this go down the chain through the parent somehow
            Object component = components.computeIfAbsent(
                    type,
                    t -> new Component<>(type.getSimpleName(), config(),
                                         c -> c.getOptionalComponent(type).orElseGet(defaultImpl))
            ).get();
            return type.cast(component);
        }

        @Override
        public List<Module> getModules() {
            return List.copyOf(modules);
        }
    }
}
