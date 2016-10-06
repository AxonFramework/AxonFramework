/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
}
