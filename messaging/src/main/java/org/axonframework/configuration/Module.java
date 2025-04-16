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

/**
 * Interface describing a module of Axon Framework's configuration API.
 * <p>
 * Modules are relatively independent. They can be {@link ComponentRegistry#registerModule(Module) registered} on a
 * parent {@link ApplicationConfigurer} or registered in a nested style on another {@link Module} through the dedicated
 * register module operation. Furthermore, a module is able to access the registered {@link Component Components} from
 * the parent {@code ApplicationConfigurer} it is registered too. However, the parent is <b>not</b> able to retrieve
 * components from these {@code Modules}, ensuring encapsulation.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface Module {

    /**
     * The identifying name of {@code this Module}.
     *
     * @return The identifying name of {@code this Module}.
     */
    String name();

    /**
     * Builds {@code this Module}, resulting in the {@link Configuration} containing all registered components.
     * <p>
     * The given {@code parent} allows access to components that have been registered with it. Note that this operation
     * is typically invoked through {@link ApplicationConfigurer#build()} and as such should not be invoked directly.
     *
     * @param parent            The parent {@code Configuration} {@code this Module} belongs in, giving it access to the
     *                          parent's components.
     * @param lifecycleRegistry The registry where lifecycle handlers can be registered by this module.
     * @return The fully initialized {@link Configuration} instance from {@code this Module} specifically.
     */
    Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry);
}
