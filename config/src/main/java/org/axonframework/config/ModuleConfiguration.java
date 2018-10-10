/*
 * Copyright (c) 2010-2018. Axon Framework
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
 */
public interface ModuleConfiguration {

    /**
     * Initialize the module configuration using the given global {@code config}
     *
     * @param config the global configuration, providing access to generic components
     */
    void initialize(Configuration config);

    /**
     * Defines a phase in which this module's {@link #initialize(Configuration)}, {@link #start()}, {@link #shutdown()}
     * will be invoked.
     *
     * @return this module's phase
     */
    default int phase() {
        return 0;
    }

    /**
     * Invoked when the Configuration is started.
     *
     * @see Configuration#start()
     */
    void start();

    /**
     * Invoked prior to shutdown of the application.
     *
     * @see Configuration#shutdown()
     */
    void shutdown();

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
