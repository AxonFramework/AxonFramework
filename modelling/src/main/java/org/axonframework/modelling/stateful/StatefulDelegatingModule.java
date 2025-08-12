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

public class StatefulDelegatingModule<M extends Module> extends DelegatingModule<M>
        implements Stateful.EntitiesPhase<M> {

    private final List<EntityModule<?, ?>> entityModules = new ArrayList<>();

    public StatefulDelegatingModule(@Nonnull M delegate) {
        super(delegate);
    }

    public StatefulDelegatingModule(@Nonnull ModuleBuilder<M> delegate) {
        super(delegate);
    }

    @Override
    public M withEntities(@Nonnull EntityModule<?, ?>... entityModules) {
        this.entityModules.addAll(List.of(entityModules));
        return delegate;
    }

    @Override
    public M withEntities(@Nonnull List<EntityModule<?, ?>> entityModules) {
        this.entityModules.addAll(entityModules);
        return delegate;
    }

    @Override
    public M build() {
        var built = super.build();
        registerStateManager();
        registerEntityModules();
        return built;
    }

    private void registerStateManager() {
        componentRegistry(cr -> cr.registerComponent(StateManager.class, config ->
                SimpleStateManager.named("StateManager[" + name() + "]")));
    }

    private void registerEntityModules() {
        componentRegistry(cr -> entityModules.forEach(cr::registerModule));
    }
}
