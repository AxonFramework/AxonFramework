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
import org.axonframework.common.annotation.Internal;

import java.util.Optional;
import java.util.function.Function;

/**
 * This module used in a context of lazy module initialization and delegates the creation of the module to its build
 * phase. Designed for internal usage only.
 * <p>
 * Its usage is required if the module under construction needs to access components (via {@link Configuration}) which
 * become available at a later stage. For this purpose, the construction is delayed by encapsulating into a function
 * call, invoked on the module build phase.
 * <p>
 * This module represents a workaround for the cases above, since the {@link ModuleBuilder} provides no means to access
 * {@link Configuration} or other components by design.
 *
 * @param <S> Type of module to configure lazily.
 * @author Simon Zambrovski
 * @since 5.0.0
 */
@Internal
public class LazyInitializedModule<S extends Module> extends BaseModule<LazyInitializedModule<S>> {

    private final Function<Configuration, ModuleBuilder<? extends Module>> moduleBuilder;
    private String moduleName;

    /**
     * Constructs the module.
     *
     * @param name          A module name used for module description.
     * @param moduleBuilder A function to be invoked on module build phase, passing the configuration to it.
     */
    public LazyInitializedModule(@Nonnull String name,
                                 @Nonnull Function<Configuration, ModuleBuilder<? extends Module>> moduleBuilder) {
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
                                 () -> {
                                     throw new IllegalStateException(
                                             "Passed configuration contained no component registry.");
                                 }
                );

        return configuration;
    }
}
