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

package org.axonframework.micrometer;

import org.axonframework.config.LegacyConfigurer;
import org.axonframework.config.ConfigurerModule;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;

/**
 * Implementation of the {@link ConfigurerModule} which uses the
 * {@link GlobalMetricRegistry} to register several Metrics Modules to the given
 * {@link LegacyConfigurer}.
 *
 * @author Steven van Beelen
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1
 */
public class MetricsConfigurerModule implements ConfigurationEnhancer {

    private final GlobalMetricRegistry globalMetricRegistry;
    private final boolean useDimensions;

    public MetricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry) {
        this(globalMetricRegistry, false);
    }

    public MetricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry, boolean useDimensions) {
        this.globalMetricRegistry = globalMetricRegistry;
        this.useDimensions = useDimensions;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry configurer) {
        if (useDimensions) {
//            globalMetricRegistry.registerWithConfigurerWithDefaultTags(configurer);
        } else {
//            globalMetricRegistry.registerWithConfigurer(configurer);
        }
    }
}
