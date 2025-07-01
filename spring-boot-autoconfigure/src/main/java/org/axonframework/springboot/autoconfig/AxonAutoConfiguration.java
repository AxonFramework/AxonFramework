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
 * Lifecycle handlers from the {@link org.axonframework.configuration.LifecycleRegistry} are managed by the Spring
 * Application context to allow "weaving" of these lifecycles with the Spring lifecycles.
 * <p>
 * This autoconfiguration will construct the beans such that they allow for a hierarchical Spring Application Context.
 *
 * @author Allard Buijze
 * @author Josh Long
 * @since 3.0.0
 */
@AutoConfiguration
public class AxonAutoConfiguration {

    /**
     * Bean creation method registering a {@link SpringComponentRegistry} <b>if</b> the current Application Context does
     * not have one already.
     * <p>
     * By only checking the {@link SearchStrategy#CURRENT current} context, we ensure that every context for a
     * hierarchical Spring Application Context receives a {@link org.axonframework.configuration.ComponentRegistry}
     * instance.
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    SpringComponentRegistry springComponentRegistry(ApplicationContext applicationContext,
                                                    SpringLifecycleRegistry springLifecycleRegistry) {
        return new SpringComponentRegistry(applicationContext, springLifecycleRegistry);
    }

    /**
     * Bean creation method registering a {@link SpringLifecycleRegistry} when there is none <b>anywhere</b>.
     * <p>
     * By checking {@link SearchStrategy#ALL everywhere}, we ensure that there only is a single
     * {@link org.axonframework.configuration.LifecycleRegistry} at all times. Even when a hierarchical Spring
     * Application Context is in place.
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringLifecycleRegistry springLifecycleRegistry() {
        return new SpringLifecycleRegistry();
    }

    /**
     * Bean creation method registering a {@link SpringAxonApplication} when there is none <b>anywhere</b>.
     * <p>
     * By checking {@link SearchStrategy#ALL everywhere}, we ensure that there only is a single main
     * {@link org.axonframework.configuration.ApplicationConfigurer} at all times. Even when a hierarchical Spring
     * Application Context is in place.
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.ALL)
    SpringAxonApplication axonApplication(SpringComponentRegistry springComponentRegistry,
                                          SpringLifecycleRegistry springLifecycleRegistry) {
        return new SpringAxonApplication(springComponentRegistry, springLifecycleRegistry);
    }

    /**
     * Bean creation method registering a {@link AxonConfiguration} if the Application Context does not have one
     * already
     * <b>and</b> if the current Application Context has a {@link SpringAxonApplication}.
     * <p>
     * By only checking the {@link SearchStrategy#CURRENT current} context for a {@code SpringAxonApplication}, we
     * ensure that there only is a single main {@link AxonConfiguration} at all times. Even when a hierarchical Spring
     * Application Context is in place.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(value = SpringAxonApplication.class, search = SearchStrategy.CURRENT)
    AxonConfiguration axonApplicationConfiguration(SpringAxonApplication axonApplication) {
        return axonApplication.build();
    }

    /**
     * Bean creation method registering a {@link Configuration} <b>if</b> the current Application Context does not have
     * one already.
     * <p>
     * This bean is <b>only</b> created when a hierarchical Spring Application Context is being used, since only then
     * will it <b>not</b> clash with the {@link #axonApplicationConfiguration(SpringAxonApplication)}. This holds since
     * the returned {@link AxonConfiguration} is a {@code Configuration} instance.
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    @ConditionalOnBean(value = AxonConfiguration.class, search = SearchStrategy.ALL)
    Configuration axonConfiguration(SpringComponentRegistry springComponentRegistry) {
        return springComponentRegistry.configuration();
    }
}
