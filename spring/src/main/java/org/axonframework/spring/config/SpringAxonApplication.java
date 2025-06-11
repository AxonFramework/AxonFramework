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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class SpringAxonApplication implements ApplicationConfigurer {

    private final SpringLifecycleRegistry springLifecycleRegistry;
    private final SpringComponentRegistry springComponentRegistry;

    @Autowired
    public SpringAxonApplication(SpringComponentRegistry springComponentRegistry,
                                 SpringLifecycleRegistry springLifecycleRegistry) {
        this.springLifecycleRegistry = springLifecycleRegistry;
        this.springComponentRegistry = springComponentRegistry;
    }

    @Override
    public ApplicationConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        componentRegistrar.accept(springComponentRegistry);
        return this;
    }

    @Override
    public ApplicationConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        lifecycleRegistrar.accept(springLifecycleRegistry);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return new AxonConfiguration() {
            @Override
            public void start() {
                // ignore, connected to Spring lifecycle
            }

            @Override
            public void shutdown() {
                // ignore, connected to Spring Lifecycle
            }

            @Override
            public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type, @Nonnull String name) {
                return springComponentRegistry.configuration().getOptionalComponent(type, name);
            }

            @Nonnull
            @Override
            public <C> C getComponent(@Nonnull Class<C> type) {
                return springComponentRegistry.configuration().getComponent(type);
            }

            @Nonnull
            @Override
            public <C> C getComponent(@Nonnull Class<C> type, @Nonnull Supplier<C> defaultImpl) {
                return springComponentRegistry.configuration().getComponent(type, defaultImpl);
            }

            @Nonnull
            @Override
            public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name) {
                return springComponentRegistry.configuration().getComponent(type, name);
            }

            @Override
            public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type) {
                return springComponentRegistry.configuration().getOptionalComponent(type);
            }

            @Nonnull
            @Override
            public <C> C getComponent(@Nonnull Class<C> type, @Nonnull String name, @Nonnull Supplier<C> defaultImpl) {
                return springComponentRegistry.configuration().getComponent(type, name, defaultImpl);
            }

            @Override
            public List<Configuration> getModuleConfigurations() {
                return springComponentRegistry.configuration().getModuleConfigurations();
            }

            @Override
            public Optional<Configuration> getModuleConfiguration(@Nonnull String name) {
                return springComponentRegistry.configuration().getModuleConfiguration(name);
            }

            @Nullable
            @Override
            public Configuration getParent() {
                return springComponentRegistry.configuration().getParent();
            }

            @Override
            public void describeTo(@Nonnull ComponentDescriptor descriptor) {
                springComponentRegistry.describeTo(descriptor);
            }
        };

    }
}
