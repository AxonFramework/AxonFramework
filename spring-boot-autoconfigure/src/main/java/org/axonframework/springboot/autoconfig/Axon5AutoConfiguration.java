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
import org.axonframework.spring.SpringAxonApplication;
import org.axonframework.spring.SpringComponentRegistry;
import org.axonframework.spring.SpringLifecycleRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
public class Axon5AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringAxonApplication axonApplication(SpringComponentRegistry springComponentRegistry,
                                          SpringLifecycleRegistry springLifecycleRegistry) {
        return new SpringAxonApplication(springComponentRegistry, springLifecycleRegistry);
    }

    @Bean
    @ConditionalOnBean(value = SpringAxonApplication.class, search = SearchStrategy.CURRENT)
    @ConditionalOnMissingBean
    AxonConfiguration axonApplicationConfiguration(SpringAxonApplication axonApplication) {
        return axonApplication.build();
    }

    // TODO - SpringComponentRegistry should not implement NewConfiguration.
    // Instead, define a bean that is created if a bean of type AxonConfiguration (or SpringAxonApplication) doesn't
    // exist in the current context. The @Primary annotation can then be removed from SpringComponentRegistry

    @Primary
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    SpringComponentRegistry axonConfiguration(SpringLifecycleRegistry springLifecycleRegistry,
                                              ApplicationContext applicationContext) {
        return new SpringComponentRegistry(springLifecycleRegistry, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringLifecycleRegistry springLifecycleRegistry() {
        return new SpringLifecycleRegistry();
    }
}
