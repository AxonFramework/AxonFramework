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

package org.axonframework.springboot.autoconfig;

import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.Configuration;
import org.axonframework.spring.config.SpringAxonApplication;
import org.axonframework.spring.config.SpringComponentRegistry;
import org.axonframework.spring.config.SpringLifecycleRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration class that defines the {@link AxonConfiguration} and
 * {@link org.axonframework.configuration.ComponentRegistry} implementations that supports beans to be accessed as
 * components.
 * <p>
 * Lifecycle handlers are managed by the Spring Application context to allow "weaving" of these lifecycles with the
 * Spring lifecycles.
 *
 * @author Allard Buijze
 * @author Josh Long
 * @since 3.0.0
 */
@AutoConfiguration
public class AxonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    SpringComponentRegistry springComponentRegistry(ApplicationContext applicationContext) {
        return new SpringComponentRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringLifecycleRegistry springLifecycleRegistry() {
        return new SpringLifecycleRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringAxonApplication axonApplication(SpringComponentRegistry springComponentRegistry,
                                          SpringLifecycleRegistry springLifecycleRegistry) {
        return new SpringAxonApplication(springComponentRegistry, springLifecycleRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(value = SpringAxonApplication.class, search = SearchStrategy.CURRENT)
    AxonConfiguration axonApplicationConfiguration(SpringAxonApplication axonApplication) {
        return axonApplication.build();
    }

    /**
     * This bean is <b>only</b> created when a hierarchical Spring Application Context is being used.
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    @ConditionalOnBean(value = AxonConfiguration.class, search = SearchStrategy.ALL)
    Configuration axonConfiguration(SpringComponentRegistry springComponentRegistry) {
        return springComponentRegistry.configuration();
    }
}
