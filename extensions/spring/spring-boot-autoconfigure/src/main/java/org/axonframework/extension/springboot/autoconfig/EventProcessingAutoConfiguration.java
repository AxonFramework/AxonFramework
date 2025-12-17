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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.extension.spring.config.EventProcessorSettings;
import org.axonframework.extension.springboot.EventProcessorProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Auto configuration for event processors.
 *
 * @author Milan Savic
 * @author Simon Zambrovski
 * @since 4.0
 */
@AutoConfiguration
public class EventProcessingAutoConfiguration {

    @Bean
    public EventProcessorSettings.MapWrapper eventProcessorSettings(Environment environment) {
        Binder binder = Binder.get(environment);

        // Bind the same structure that EventProcessorProperties would expose
        Map<String, EventProcessorProperties.ProcessorSettings> raw =
                binder.bind("axon.eventhandling.processors",
                            Bindable.mapOf(String.class, EventProcessorProperties.ProcessorSettings.class))
                      .orElseGet(java.util.HashMap::new);

        Map<String, EventProcessorSettings> map = new java.util.HashMap<>(raw);
        // Retain the default behavior
        map.putIfAbsent(EventProcessorSettings.DEFAULT, new EventProcessorProperties.ProcessorSettings());

        return new EventProcessorSettings.MapWrapper(map);
    }
}
