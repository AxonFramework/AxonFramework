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
import org.axonframework.configuration.SingleComponentModule;
import org.axonframework.modelling.configuration.EntityModule;
import org.axonframework.modelling.configuration.StatefulComponentBuilder;

import java.util.List;

// todo: rename to StatefulModule ?
public interface Stateful<M extends Module> extends ModuleBuilder<M> {

    static <M extends Module> EntitiesPhase<M> module(M module) {
        return new StatefulDelegatingModule<M>(module);
    }

    static <M extends Module> EntitiesPhase<M> module(ModuleBuilder<M> moduleBuilder) {
        return new StatefulDelegatingModule<M>(moduleBuilder);
    }

    static <C> EntitiesPhase<SingleComponentModule<C>> module(
            String name, Class<C> componentClazz,
            StatefulComponentBuilder<C> moduleBuilder
    ) {
        return new StatefulDelegatingModule<SingleComponentModule<C>>(
                new SingleComponentModule<>(name, componentClazz, moduleBuilder)
        );
    }

    interface EntitiesPhase<M extends Module> {

        Stateful<M> withEntities(@Nonnull EntityModule<?, ?>... entityModules); // todo: at least one!

        Stateful<M> withEntities(@Nonnull List<EntityModule<?, ?>> entityModules);
    }
}
