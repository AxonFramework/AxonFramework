/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.common.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Base implementation for custom modules that contains the ComponentRegistry required to register components, enhancers
 * and (sub)modules.
 *
 * @param <S> The type extending this module, to support fluent interfaces.
 * @author Allard Buijze
 * @since 5.0.0
 */
public abstract class BaseModule<S extends BaseModule<S>> implements Module {

    protected final String name;
    private final List<Consumer<ComponentRegistry>> registryActions = new ArrayList<>();
    private final AtomicBoolean built = new AtomicBoolean(false);

    /**
     * Construct a base module with the given {@code name}.
     *
     * @param name The name of this module. Must not be {@code null}.
     */
    protected BaseModule(@Nonnull String name) {
        this.name = Assert.nonEmpty(name, "The Module name cannot be null or empty.");
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent,
                               @Nonnull LifecycleRegistry lifecycleRegistry) {

        var registry = Optional.of(parent.getComponent(ComponentRegistry.class,
                                                       () ->
                                                           new DefaultComponentRegistry().disableEnhancerScanning()
                                                       ))
                               .get();

        if (!(registry instanceof DefaultComponentRegistry componentRegistry)) {
            throw new IllegalStateException("BaseModule requires a DefaultComponentRegistry to build its configuration.");
        }

        registryActions.forEach(action -> action.accept(componentRegistry));

        built.set(true);
        return postProcessConfiguration(componentRegistry.buildNested(parent, lifecycleRegistry));
    }

    /**
     * Method that can be overridden to specify specific actions that need to be taken after the configuration for this
     * module is constructed.
     * <p>
     * The default implementation returns the configuration unchanged.
     *
     * @param moduleConfiguration The configuration for this module.
     */
    @SuppressWarnings("unused")
    protected Configuration postProcessConfiguration(Configuration moduleConfiguration) {
        return moduleConfiguration;
    }

    /**
     * Executes the given {@code registryAction} on the {@link ComponentRegistry} associated with this module during the
     * module build.
     * <p>
     * This operation must be invoked before the module has been built.
     *
     * @param registryAction The action to perform on the component registry.
     * @return This instance for fluent interfacing.
     */
    public S componentRegistry(@Nonnull Consumer<ComponentRegistry> registryAction) {
        if (built.get()) {
            throw new IllegalStateException("Module has already been built.");
        }
        this.registryActions.add(Objects.requireNonNull(registryAction, "The registryAction must be null."));
        //noinspection unchecked
        return (S) this;
    }
}
