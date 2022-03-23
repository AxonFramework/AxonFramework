/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.metrics;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;

import javax.annotation.Nonnull;

/**
 * Implementation of the {@link ConfigurerModule} which uses the {@link org.axonframework.metrics.GlobalMetricRegistry}
 * to register several Metrics Modules to the given {@link org.axonframework.config.Configurer}.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
public class MetricsConfigurerModule implements ConfigurerModule {

    private final GlobalMetricRegistry globalMetricRegistry;

    public MetricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry) {
        this.globalMetricRegistry = globalMetricRegistry;
    }

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        globalMetricRegistry.registerWithConfigurer(configurer);
    }
}
