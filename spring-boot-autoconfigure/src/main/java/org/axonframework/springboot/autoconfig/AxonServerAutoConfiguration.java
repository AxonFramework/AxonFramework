/*
 * Copyright (c) 2010-2023. Axon Framework
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


import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ManagedChannelCustomizer;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.PriorityResolver;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.TagsConfigurationProperties;
import org.axonframework.springboot.service.connection.AxonServerConnectionDetails;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.annotation.Nonnull;

/**
 * Configures Axon Server as implementation for the CommandBus, QueryBus and EventStore.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@EnableConfigurationProperties(TagsConfigurationProperties.class)
@ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
public class AxonServerAutoConfiguration {

    @Configuration
    @ConditionalOnMissingClass("org.springframework.boot.autoconfigure.service.connection.ConnectionDetails")
    public static class ConnectionConfiguration implements ApplicationContextAware {

        private ApplicationContext applicationContext;
        @Bean
        public AxonServerConfiguration axonServerConfiguration() {
            AxonServerConfiguration configuration = new AxonServerConfiguration();
            configuration.setComponentName(clientName(applicationContext.getId()));
            return configuration;
        }
        @Override
        public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.autoconfigure.service.connection.ConnectionDetails")
    public static class ConnectionDetailsConfiguration implements ApplicationContextAware{
        private ApplicationContext applicationContext;

        @ConditionalOnMissingBean(AxonServerConnectionDetails.class)
        @Bean
        public AxonServerConfiguration axonServerConfiguration() {
            AxonServerConfiguration configuration = new AxonServerConfiguration();
            configuration.setComponentName(clientName(applicationContext.getId()));
            return configuration;
        }

        @ConditionalOnBean(type = "org.axonframework.springboot.service.connection.AxonServerConnectionDetails")
        @Bean
        public AxonServerConfiguration axonServerConfigurationWithConnectionDetails(AxonServerConnectionDetails connectionDetails) {
            AxonServerConfiguration configuration = new AxonServerConfiguration();
            configuration.setComponentName(clientName(applicationContext.getId()));
            configuration.setServers(connectionDetails.routingServers());
            return configuration;
        }

        @Override
        public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }
    }


    private static String clientName(@Nullable String id) {
        if (id == null) {
            return "Unnamed";
        } else if (id.contains(":")) {
            return id.substring(0, id.indexOf(":"));
        }
        return id;
    }

    @Bean
    @ConditionalOnMissingBean
    public ManagedChannelCustomizer managedChannelCustomizer() {
        return ManagedChannelCustomizer.identity();
    }

    @Bean
    public AxonServerConnectionManager platformConnectionManager(AxonServerConfiguration axonServerConfiguration,
                                                                 TagsConfigurationProperties tagsConfigurationProperties,
                                                                 ManagedChannelCustomizer managedChannelCustomizer) {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(axonServerConfiguration)
                                          .tagsConfiguration(tagsConfigurationProperties.toTagsConfiguration())
                                          .channelCustomizer(managedChannelCustomizer)
                                          .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return new AnnotationRoutingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public PriorityResolver<CommandMessage<?>> commandPriorityCalculator() {
        return message -> 0;
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryPriorityCalculator queryPriorityCalculator() {
        return QueryPriorityCalculator.defaultQueryPriorityCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryInvocationErrorHandler queryInvocationErrorHandler() {
        return LoggingQueryInvocationErrorHandler.builder().build();
    }

    @ConditionalOnMissingBean
    @Bean
    public TargetContextResolver<Message<?>> targetContextResolver() {
        return TargetContextResolver.noOp();
    }

    @Bean
    @ConditionalOnMissingClass("org.axonframework.extensions.multitenancy.autoconfig.MultiTenancyAxonServerAutoConfiguration")
    public EventProcessorInfoConfiguration processorInfoConfiguration(
            EventProcessingConfiguration eventProcessingConfiguration,
            AxonServerConnectionManager connectionManager,
            AxonServerConfiguration configuration) {
        return new EventProcessorInfoConfiguration(c -> eventProcessingConfiguration,
                                                   c -> connectionManager,
                                                   c -> configuration);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public EventScheduler eventScheduler(@Qualifier("eventSerializer") Serializer eventSerializer,
                                         AxonServerConnectionManager connectionManager) {
        return AxonServerEventScheduler.builder()
                                       .eventSerializer(eventSerializer)
                                       .connectionManager(connectionManager)
                                       .build();
    }
}

