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

import org.axonframework.config.ConfigurerModule;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.spring.config.MessageHandlerLookup;
import org.axonframework.spring.config.SpringAggregateLookup;
import org.axonframework.spring.config.SpringSagaLookup;
import org.axonframework.spring.config.annotation.HandlerDefinitionFactoryBean;
import org.axonframework.spring.config.annotation.SpringParameterResolverFactoryBean;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;

import java.util.List;

/**
 * Infrastructure autoconfiguration class for Axon Framework application. Constructs the look-up components, like the
 * {@link MessageHandlerLookup} to find Axon components and register them with the {@code SpringConfigurer}.
 *
 * @author Allard Buijze
 * @since 3.0.4
 */
@AutoConfiguration
@AutoConfigureAfter({
        LegacyAxonAutoConfiguration.class,
        JpaAutoConfiguration.class,
        JpaEventStoreAutoConfiguration.class,
        NoOpTransactionAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
public class InfraConfiguration {

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static MessageHandlerLookup messageHandlerLookup() {
        return new MessageHandlerLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringAggregateLookup springAggregateLookup() {
        return new SpringAggregateLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringSagaLookup springSagaLookup() {
        return new SpringSagaLookup();
    }

    // TODO #3075
    // Left this block of code on purpose, as it served a rather important purpose for lifecycle management in combination with Spring
    // This should be brought over correctly!
//    @Bean
//    public InitializingBean lifecycleInitializer(LegacyConfigurer configurer,
//                                                 List<Lifecycle> lifecycleBeans) {
//        return () -> configurer.onInitialize(
//                config -> lifecycleBeans.forEach(bean -> bean.registerLifecycleHandlers(config.lifecycleRegistry()))
//        );
//    }

    @Primary
    @Bean
    public HandlerDefinitionFactoryBean handlerDefinition(List<HandlerDefinition> handlerDefinitions,
                                                          List<HandlerEnhancerDefinition> handlerEnhancerDefinitions) {
        return new HandlerDefinitionFactoryBean(handlerDefinitions, handlerEnhancerDefinitions);
    }

    @Primary
    @Bean
    public SpringParameterResolverFactoryBean parameterResolverFactory(
            List<ParameterResolverFactory> parameterResolverFactories
    ) {
        SpringParameterResolverFactoryBean springParameterResolverFactoryBean = new SpringParameterResolverFactoryBean();
        springParameterResolverFactoryBean.setAdditionalFactories(parameterResolverFactories);
        return springParameterResolverFactoryBean;
    }

    @ConditionalOnClass(CorrelationDataProvider.class)
    @Bean
    public ConfigurerModule correlationDataProvidersConfigurer(List<CorrelationDataProvider> correlationDataProviders) {
        return configurer -> configurer.configureCorrelationDataProviders(c -> correlationDataProviders);
    }

    @ConditionalOnClass(EventUpcaster.class)
    @Bean
    public ConfigurerModule eventUpcastersConfigurer(List<EventUpcaster> upcasters) {
        return configurer -> upcasters.forEach(u -> configurer.registerEventUpcaster(c -> u));
    }

    @ConditionalOnMissingBean
    @Bean
    public ResourceInjector resourceInjector() {
        return new SpringResourceInjector();
    }
}