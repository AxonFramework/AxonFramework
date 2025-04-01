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
import org.axonframework.messaging.annotation.ParameterResolverFactoryConfigurationDefaults;

import java.util.function.Consumer;

/**
 * Base implementation for custom modules that contains the ComponentRegistry required to register components, enhancers
 * and (sub)modules. Registers the {@link ParameterResolverFactoryConfigurationDefaults} as a default enhancer,
 * providing access in message handlers to the {@link ComponentRegistry} of the module.
 *
 * @param <S> The type extending this module, to support fluent interfaces
 */
public abstract class BaseModule<S extends BaseModule<S>> implements Module {

    private final DefaultComponentRegistry componentRegistry;
    private final String name;

    /**
     * Construct a base module with the given {@code name}.
     *
     * @param name The name of this module. Must not be {@code null}
     */
    protected BaseModule(@Nonnull String name) {
        Assert.nonEmpty(name, "The Module name cannot be null or empty.");
        this.name = name;
        this.componentRegistry = new DefaultComponentRegistry();
        this.componentRegistry.registerEnhancer(new ParameterResolverFactoryConfigurationDefaults());
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public NewConfiguration build(@Nonnull NewConfiguration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        return postProcessConfiguration(componentRegistry.buildNested(parent, lifecycleRegistry));
    }

    /**
     * Method that can be overridden to specify specific actions that need to be taken after the configuration for this
     * module is constructed.
     * <p>
     * The default implementation returns the configuration unchanged
     *
     * @param moduleConfiguration The configuration for this module
     */
    @SuppressWarnings("unused")
    protected NewConfiguration postProcessConfiguration(NewConfiguration moduleConfiguration) {
        return moduleConfiguration;
    }

    /**
     * Executes the given {@code registryAction} on the {@link ComponentRegistry} associated with this module.
     *
     * @param registryAction the action to perform on the component registry
     * @return this instance for fluent interfacing
     */
    public S componentRegistry(Consumer<ComponentRegistry> registryAction) {
        registryAction.accept(this.componentRegistry);
        //noinspection unchecked
        return (S) this;
    }
}
