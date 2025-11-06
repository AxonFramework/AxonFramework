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

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * The HierarchicalConfiguration is a configuration that will ensure the to-be-built child configuration will be passed
 * a {@link LifecycleRegistry} that is a child of the parent configuration's {@link LifecycleRegistry}. This will ensure
 * that the child configuration's start- and shutdown handlers receive the child configuration, so they can retrieve
 * their own components.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class HierarchicalConfiguration implements LifecycleRegistry {

    private final LifecycleRegistry parentLifecycleRegistry;
    private final Configuration childConfiguration;

    /**
     * Builds a {@link Configuration} based on the passed {@code childConfigurationBuilder}. This builder will
     * receive a {@link LifecycleRegistry} that is a child of the passed {@code parentLifecycleRegistry}.  The builder
     * is created with a new {@link LifecycleRegistry} that is scoped to the child configuration. This will ensure child
     * configuration lifecycle handlers are called with the child configuration, not with the parent, which would leave
     * them unable to find their own components due to the encapsulation.
     *
     * @param parentLifecycleRegistry   The parent {@link LifecycleRegistry} to build the child configuration from.
     * @param childConfigurationBuilder The builder that will be used to create the child configuration. It will receive
     *                                  a {@link LifecycleRegistry} that is a child of the
     *                                  {@code parentLifecycleRegistry}.
     * @return The child configuration that was built using the passed {@code childConfigurationBuilder}.
     */
    public static Configuration build(
            LifecycleRegistry parentLifecycleRegistry,
            Function<LifecycleRegistry, Configuration> childConfigurationBuilder
    ) {
        HierarchicalConfiguration hierarchicalConfiguration = new HierarchicalConfiguration(parentLifecycleRegistry,
                                                                                            childConfigurationBuilder);
        return hierarchicalConfiguration.getConfiguration();
    }

    private Configuration getConfiguration() {
        return childConfiguration;
    }

    private HierarchicalConfiguration(
            @Nonnull LifecycleRegistry parentLifecycleRegistry,
            @Nonnull Function<LifecycleRegistry, Configuration> childConfigurationBuilder) {
        this.parentLifecycleRegistry = requireNonNull(parentLifecycleRegistry,
                                                      "parentLifecycleRegistry may not be null");
        this.childConfiguration = childConfigurationBuilder.apply(this);
    }

    @Override
    public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        parentLifecycleRegistry.registerLifecyclePhaseTimeout(timeout, timeUnit);
        return this;
    }

    @Override
    public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        parentLifecycleRegistry.onStart(phase, (parentConfiguration) -> {
            return startHandler.run(childConfiguration);
        });
        return this;
    }

    @Override
    public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        parentLifecycleRegistry.onShutdown(phase, (parentConfiguration) -> {
            return shutdownHandler.run(childConfiguration);
        });
        return this;
    }
}
