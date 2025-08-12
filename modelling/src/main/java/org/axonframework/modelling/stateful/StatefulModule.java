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

package org.axonframework.modelling.stateful;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.*;
import org.axonframework.configuration.Module;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class StatefulModule<T extends org.axonframework.configuration.Module> implements Module, ModuleBuilder<T> {

    private final EntitiesConfigurer entitiesConfigurer = new EntitiesConfigurer();
    private final ModuleBuilder<T> delegate;
    private final AtomicReference<T> buildModule = new AtomicReference<>();

    public StatefulModule(
            UnaryOperator<EntitiesConfigurer> configurer,
            ModuleBuilder<T> delegate
    ) {
        this.delegate = delegate;
        configurer.apply(entitiesConfigurer);
    }

    public StatefulModule(
            UnaryOperator<EntitiesConfigurer> configurer,
            T delegate
    ) {
        this.delegate = () -> delegate;
        configurer.apply(entitiesConfigurer);
    }

    @Override
    public String name() {
        return getBuilt().name();
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        return getBuilt().build(parent, lifecycleRegistry);
    }

    @Override
    public T componentRegistry(@Nonnull Consumer<ComponentRegistry> registryAction) {
        //noinspection unchecked
        return (T) getBuilt().componentRegistry(registryAction);
    }

    @Override
    public T build() {
        registerStateManager();
        return getBuilt();
    }

    private void registerStateManager() {
        componentRegistry(cr -> cr.registerComponent(StateManager.class, config ->
                SimpleStateManager.named("StateManager[" + name() + "]")));
    }

    private void registerEntityModules() {
        componentRegistry(cr -> entitiesConfigurer.entityModules().values().forEach(cr::registerModule));
    }

    private T getBuilt() {
        T built = buildModule.get();
        if (built == null) {
            built = delegate.build();
            if (!buildModule.compareAndSet(null, built)) {
                built = buildModule.get();
            }
        }
        return built;
    }
}
