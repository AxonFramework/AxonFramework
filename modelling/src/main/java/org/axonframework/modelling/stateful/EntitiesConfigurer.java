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
import org.axonframework.modelling.configuration.EntityModule;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class EntitiesConfigurer {

    private final Map<String, EntityModule<?, ?>> entityModules;

    public EntitiesConfigurer() {
        this.entityModules = new HashMap<>();
    }

    public <I, E> EntitiesConfigurer entity(@Nonnull EntityModule<I, E> entityModule) {
        requireNonNull(entityModule, "The entity module cannot be null.");
        entityModules.put(entityModule.entityName(), entityModule);
        return this;
    }

    public Map<String, EntityModule<?, ?>> entityModules() {
        return Map.copyOf(entityModules);
    }
}
