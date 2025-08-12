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

public class SingleComponentModule<C> extends BaseModule<SingleComponentModule<C>>
        implements ModuleBuilder<SingleComponentModule<C>> {

    private final ComponentDefinition<C> componentDefinition;

    /**
     * Construct a base module with the given {@code name}.
     *
     * @param name The name of this module. Must not be {@code null}.
     */
    public SingleComponentModule(
            @Nonnull String name,
            @Nonnull Class<C> componentClazz,
            @Nonnull ComponentBuilder<C> componentBuilder
    ) {
        this(name, ComponentDefinition.ofTypeAndName(componentClazz, name).withBuilder(componentBuilder));
    }

    /**
     * Construct a base module with the given {@code name}.
     *
     * @param name The name of this module. Must not be {@code null}.
     */
    public SingleComponentModule(
            @Nonnull String name,
            @Nonnull ComponentDefinition<C> componentDefinition
    ) {
        super(name);
        this.componentDefinition = componentDefinition;
    }

    @Override
    public SingleComponentModule<C> build() {
        componentRegistry(cr -> cr.registerComponent(componentDefinition));
        return this;
    }
}