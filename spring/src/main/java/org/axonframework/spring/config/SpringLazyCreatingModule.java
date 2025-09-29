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
import org.axonframework.common.annotations.Internal;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;

import java.util.Optional;
import java.util.function.Function;

/**
 * This module used in a context of lazy module initialization in Spring
 * and delegates the creation of the module to its build phase.
 * <p>
 * Its usage is required if the module under construction needs to access
 * components and Spring beans during initialization which become available at a later stage.
 * For this purpose, the construction is delayed by encapsulating into a function call,
 * invoked on the module build phase.
 *
 * @param <S> Type of module to configure lazily.
 * @author Simon Zambrovski
 * @since 5.0.0
 */
@Internal
public class SpringLazyCreatingModule<S extends Module> extends BaseModule<SpringLazyCreatingModule<S>> {

    private final Function<Configuration, ModuleBuilder<? extends Module>> moduleBuilder;
    private String moduleName;

    SpringLazyCreatingModule(String name, Function<Configuration, ModuleBuilder<? extends Module>> moduleBuilder) {
        super(name);
        this.moduleBuilder = moduleBuilder;
    }

    @Override
    public String name() {
        // this is a little strange, but this method is used
        // several times during initialization
        // first to identify the name of the module during registration
        // secondly to identify the name of the resulting configuration
        // since the lazy loading module's purpose is to lazily run the module builder, we change the name of the config
        return moduleName != null ? moduleName : super.name();
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        Module module = moduleBuilder.apply(parent).build();
        this.moduleName = module.name(); // update the name for the configuration
        Configuration configuration = module.build(parent, lifecycleRegistry);

        Optional.of(parent.getComponent(ComponentRegistry.class, () -> null))
                .ifPresentOrElse((r) -> r.registerModule(module),
                                 () -> componentRegistry(r -> r.registerModule(module)));

        return configuration;
    }
}
