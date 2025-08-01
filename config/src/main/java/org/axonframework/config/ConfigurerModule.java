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

package org.axonframework.config;

import jakarta.annotation.Nonnull;

/**
 * Interface describing a configurer for a module in the Axon Configuration API. Allows the registration of modules on
 * the {@link LegacyConfigurer} like the {@link org.axonframework.monitoring.MessageMonitor}.
 *
 * @author Steven van Beelen
 * @since 3.2
 * @deprecated In favor of the {@link org.axonframework.configuration.ConfigurationEnhancer}.
 */
@Deprecated
public interface ConfigurerModule {

    /**
     * Configure this module to the given global {@link LegacyConfigurer}.
     *
     * @param configurer a {@link LegacyConfigurer} instance to configure this module with
     */
    void configureModule(@Nonnull LegacyConfigurer configurer);

    /**
     * Returns the relative order this configurer should be invoked, compared to other intstances.
     * <p>
     * Use lower (negative) values for modules providing sensible defaults, and higher values for modules overriding
     * values potentially previously set.
     *
     * @return the order in which this configurer should be invoked
     */
    default int order() {
        return 0;
    }
}
