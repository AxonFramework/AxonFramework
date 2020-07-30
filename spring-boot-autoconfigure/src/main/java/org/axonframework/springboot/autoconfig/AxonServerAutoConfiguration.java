/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.springboot.TagsConfigurationProperties;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures Axon Server as implementation for the CommandBus, QueryBus and EventStore.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@EnableConfigurationProperties(TagsConfigurationProperties.class)
@ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
public class AxonServerAutoConfiguration implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Bean
    public AxonServerConfiguration axonServerConfiguration() {
        AxonServerConfiguration configuration = new AxonServerConfiguration();
        configuration.setComponentName(clientName(applicationContext.getId()));
        return configuration;
    }

    private String clientName(String id) {
        if (id.contains(":")) {
            return id.substring(0, id.indexOf(":"));
        }
        return id;
    }

    @Bean
    public AxonServerConnectionManager platformConnectionManager(AxonServerConfiguration axonServerConfiguration,
                                                                 TagsConfigurationProperties tagsConfigurationProperties) {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(axonServerConfiguration)
                                          .tagsConfiguration(tagsConfigurationProperties.toTagsConfiguration())
                                          .build();
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(qualifier = "!localSegment", beanClass = CommandBus.class)
    public AxonServerCommandBus axonServerCommandBus(AxonServerConnectionManager axonServerConnectionManager,
                                                     AxonServerConfiguration axonServerConfiguration,
                                                     @Qualifier("localSegment") CommandBus localSegment,
                                                     @Qualifier("messageSerializer") Serializer messageSerializer,
                                                     RoutingStrategy routingStrategy,
                                                     CommandPriorityCalculator priorityCalculator,
                                                     CommandLoadFactorProvider loadFactorProvider,
                                                     TargetContextResolver<? super CommandMessage<?>> targetContextResolver) {
        return AxonServerCommandBus.builder()
                                   .axonServerConnectionManager(axonServerConnectionManager)
                                   .configuration(axonServerConfiguration)
                                   .localSegment(localSegment)
                                   .serializer(messageSerializer)
                                   .routingStrategy(routingStrategy)
                                   .priorityCalculator(priorityCalculator)
                                   .loadFactorProvider(loadFactorProvider)
                                   .targetContextResolver(targetContextResolver)
                                   .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return new AnnotationRoutingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandPriorityCalculator commandPriorityCalculator() {
        return CommandPriorityCalculator.defaultCommandPriorityCalculator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandLoadFactorProvider commandLoadFactorProvider(AxonServerConfiguration configuration) {
        return command -> configuration.getCommandLoadFactor();
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

    @Bean
    @ConditionalOnMissingBean(QueryBus.class)
    public AxonServerQueryBus queryBus(AxonServerConnectionManager axonServerConnectionManager,
                                       AxonServerConfiguration axonServerConfiguration,
                                       AxonConfiguration axonConfiguration,
                                       TransactionManager txManager,
                                       @Qualifier("messageSerializer") Serializer messageSerializer,
                                       Serializer genericSerializer,
                                       QueryPriorityCalculator priorityCalculator,
                                       QueryInvocationErrorHandler queryInvocationErrorHandler,
                                       TargetContextResolver<? super QueryMessage<?, ?>> targetContextResolver) {
        SimpleQueryBus simpleQueryBus =
                SimpleQueryBus.builder()
                              .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                              .transactionManager(txManager)
                              .queryUpdateEmitter(axonConfiguration.getComponent(QueryUpdateEmitter.class))
                              .errorHandler(queryInvocationErrorHandler)
                              .build();
        simpleQueryBus.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
        );

        return AxonServerQueryBus.builder()
                                 .axonServerConnectionManager(axonServerConnectionManager)
                                 .configuration(axonServerConfiguration)
                                 .localSegment(simpleQueryBus)
                                 .updateEmitter(simpleQueryBus.queryUpdateEmitter())
                                 .messageSerializer(messageSerializer)
                                 .genericSerializer(genericSerializer)
                                 .priorityCalculator(priorityCalculator)
                                 .targetContextResolver(targetContextResolver)
                                 .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public TargetContextResolver<Message<?>> targetContextResolver() {
        return TargetContextResolver.noOp();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean
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
    public EventStore eventStore(AxonServerConfiguration axonServerConfiguration,
                                 AxonConfiguration configuration,
                                 AxonServerConnectionManager axonServerConnectionManager,
                                 Serializer snapshotSerializer,
                                 @Qualifier("eventSerializer") Serializer eventSerializer) {
        return AxonServerEventStore.builder()
                                   .messageMonitor(configuration
                                                           .messageMonitor(AxonServerEventStore.class, "eventStore"))
                                   .configuration(axonServerConfiguration)
                                   .platformConnectionManager(axonServerConnectionManager)
                                   .snapshotSerializer(snapshotSerializer)
                                   .eventSerializer(eventSerializer)
                                   .snapshotFilter(configuration.snapshotFilter())
                                   .upcasterChain(configuration.upcasterChain())
                                   .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventScheduler eventScheduler(@Qualifier("eventSerializer") Serializer eventSerializer,
                                         AxonServerConnectionManager connectionManager) {
        return AxonServerEventScheduler.builder()
                                       .eventSerializer(eventSerializer)
                                       .connectionManager(connectionManager)
                                       .build();
    }
}

