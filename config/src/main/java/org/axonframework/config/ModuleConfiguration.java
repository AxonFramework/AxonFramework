/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config;

/**
 * Interface describing a module for the Axon Configuration API. These modules are relatively independent, but have
 * access to the component available in the main Configuration.
 * <p>
 * Modules have callback methods for the initialization, start and shutdown phases of the application's lifecycle.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public interface ModuleConfiguration {

    /**
     * Initialize the module configuration using the given global {@code config}. Any specific start up or shut down
     * processes should be added here by using the provided {@code config} and invoke {@link Configuration#onStart(int,
     * LifecycleHandler)} and {@link Configuration#onShutdown(int, LifecycleHandler)} respectively.
     *
     * @param config the global configuration, providing access to generic components
     */
    void initialize(Configuration config);

    /**
     * Returns the actual module configuration instance. Usually, it is the instance itself. However, in case of module
     * configuration wrappers, we would like to provide the wrapped module configuration as the instance.
     *
     * @return the actual module configuration instance
     */
    default ModuleConfiguration unwrap() {
        return this;
    }

    /**
     * Checks whether this Module Configuration is of the given {@code type}.
     *
     * @param type a {@link Class} type to check the Module Configuration against
     * @return whether Module Configuration is of given {@code type}
     */
    default boolean isType(Class<?> type) {
        return type.isInstance(this);
    }
}
