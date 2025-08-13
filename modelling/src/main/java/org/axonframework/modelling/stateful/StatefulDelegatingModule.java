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
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.configuration.EntityModule;

import java.util.ArrayList;
import java.util.List;

public class StatefulDelegatingModule<M extends Module> implements Stateful<M>, Stateful.EntitiesPhase<M> {

    private final List<EntityModule<?, ?>> entityModules = new ArrayList<>();
    private final M delegate;

    public StatefulDelegatingModule(@Nonnull M delegate) {
        this.delegate = delegate;
    }

    public StatefulDelegatingModule(@Nonnull ModuleBuilder<M> delegate) {
        this.delegate = delegate.build();
    }

    @Override
    public Stateful<M> withEntities(@Nonnull EntityModule<?, ?>... entityModules) {
        this.entityModules.addAll(List.of(entityModules));
        return this;
    }

    @Override
    public Stateful<M> withEntities(@Nonnull List<EntityModule<?, ?>> entityModules) {
        this.entityModules.addAll(entityModules);
        return this;
    }

    @Override
    public M build() {
        registerStateManager();
        registerEntityModules();
        return delegate;
    }

    private void registerStateManager() {
        delegate.componentRegistry(cr -> cr.registerIfNotPresent(StateManager.class, config ->
                SimpleStateManager.named("StateManager[" + delegate.name() + "]")));
    }

    private void registerEntityModules() {
        delegate.componentRegistry(cr -> entityModules.forEach(cr::registerModule));
    }
}
