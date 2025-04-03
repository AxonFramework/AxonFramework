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

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Builder for a child configuration that is built from a parent configuration. The child configuration is created with
 * a new {@link LifecycleRegistry} that is scoped to the child configuration. This will ensure child configuration
 * lifecycle handlers are called with the child configuration, not with the parent, which would leave them unable to
 * find their own components due to the encapsulation.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ChildConfigurationBuilder {

    private final LifecycleRegistry parentLifecycleRegistry;
    private final NewConfiguration childConfiguration;

    /**
     * Creates a new child configuration based on the given {@code parentConfiguration} and
     * {@code parentLifecycleRegistry}. The child configuration is built using the given {@code configurationBuilder}
     * function.
     *
     * @param parentLifecycleRegistry The parent {@link LifecycleRegistry} to use for the child configuration.
     * @param parentConfiguration     The parent {@link NewConfiguration} to use for the child configuration.
     * @param configurationBuilder    The function to build the child configuration. This function will be called with
     *                                the new {@link LifecycleRegistry} and the parent configuration.
     * @return The child {@link NewConfiguration} that was built using the given {@code configurationBuilder} function.
     */
    public static NewConfiguration buildChildConfiguration(
            @Nonnull LifecycleRegistry parentLifecycleRegistry,
            @Nonnull NewConfiguration parentConfiguration,
            @Nonnull BiFunction<LifecycleRegistry, NewConfiguration, NewConfiguration> configurationBuilder) {
        ChildConfigurationBuilder builder = new ChildConfigurationBuilder(
                parentLifecycleRegistry, parentConfiguration, configurationBuilder
        );
        return builder.getConfiguration();
    }

    private ChildConfigurationBuilder(
            @Nonnull LifecycleRegistry parentLifecycleRegistry,
            @Nonnull NewConfiguration parentConfiguration,
            @Nonnull BiFunction<LifecycleRegistry, NewConfiguration, NewConfiguration> configurationBuilder) {
        this.parentLifecycleRegistry = requireNonNull(parentLifecycleRegistry,
                                                      "parentLifecycleRegistry may not be null");
        var childLifecycleRegistry = new ChildLifecycleRegistry();
        this.childConfiguration = requireNonNull(configurationBuilder.apply(childLifecycleRegistry,
                                                                            parentConfiguration));
    }

    private class ChildLifecycleRegistry implements LifecycleRegistry {

        @Override
        public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
            parentLifecycleRegistry.registerLifecyclePhaseTimeout(timeout, timeUnit);
            return this;
        }

        @Override
        public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
            parentLifecycleRegistry.onStart(phase, (parentConfiguration) -> {
                startHandler.run(childConfiguration);
            });
            return this;
        }

        @Override
        public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
            parentLifecycleRegistry.onShutdown(phase, (parentConfiguration) -> {
                shutdownHandler.run(childConfiguration);
            });
            return this;
        }
    }

    private NewConfiguration getConfiguration() {
        return childConfiguration;
    }
}
