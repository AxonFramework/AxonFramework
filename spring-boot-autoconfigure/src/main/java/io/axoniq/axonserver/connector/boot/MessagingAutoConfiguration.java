/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.axonserver.connector.boot;


import io.axoniq.axonserver.connector.AxonServerConfiguration;
import io.axoniq.axonserver.connector.PlatformConnectionManager;
import io.axoniq.axonserver.connector.command.AxonServerCommandBus;
import io.axoniq.axonserver.connector.command.CommandPriorityCalculator;
import io.axoniq.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import io.axoniq.axonserver.connector.query.AxonServerQueryBus;
import io.axoniq.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.boot.autoconfig.AxonAutoConfiguration;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures AxonHub as implementation for the CommandBus and QueryBus
 *
 * @author Marc Gathier
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
public class MessagingAutoConfiguration  implements ApplicationContextAware  {
    private ApplicationContext applicationContext;

    @Bean
    public AxonServerConfiguration axonHubConfiguration() {
        AxonServerConfiguration configuration = new AxonServerConfiguration();
        configuration.setComponentName(clientName(applicationContext.getId()));
        return configuration;
    }

    private String clientName(String id) {
        if (id.contains(":")) return id.substring(0, id.indexOf(":"));
        return id;
    }

    @Bean
    public PlatformConnectionManager platformConnectionManager(AxonServerConfiguration routingConfiguration) {
        return new PlatformConnectionManager(routingConfiguration);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CommandBus.class)
    public AxonServerCommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration, AxonServerConfiguration axonHubConfiguration,
                                           Serializer serializer, PlatformConnectionManager platformConnectionManager,
                                           RoutingStrategy routingStrategy,
                                           CommandPriorityCalculator priorityCalculator) {

        SimpleCommandBus commandBus = new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));
        commandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));

        return new AxonServerCommandBus(platformConnectionManager, axonHubConfiguration, commandBus, serializer, routingStrategy,
                                     priorityCalculator);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return new AnnotationRoutingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandPriorityCalculator commandPriorityCalculator() {
        return new CommandPriorityCalculator() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryPriorityCalculator queryPriorityCalculator() {
        return new QueryPriorityCalculator() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryInvocationErrorHandler queryInvocationErrorHandler() {
        return new LoggingQueryInvocationErrorHandler();
    }

    @Bean
    // The Axon Hub QueryBus requires Axon 3.2+
    @ConditionalOnClass(name = {"org.axonframework.queryhandling.responsetypes.ResponseType"})
    @ConditionalOnMissingBean(QueryBus.class)
    public AxonServerQueryBus queryBus(PlatformConnectionManager platformConnectionManager, AxonServerConfiguration axonHubConfiguration,
                                       AxonConfiguration axonConfiguration, TransactionManager txManager,
                                       @Qualifier("messageSerializer") Serializer messageSerializer,
                                       Serializer genericSerializer,
                                       QueryPriorityCalculator priorityCalculator, QueryInvocationErrorHandler queryInvocationErrorHandler) {
        return new AxonServerQueryBus(platformConnectionManager, axonHubConfiguration,
                                      new SimpleQueryBus(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"),
                                                      txManager, queryInvocationErrorHandler),
                                   messageSerializer, genericSerializer, priorityCalculator);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean
    public EventProcessorInfoConfiguration processorInfoConfiguration(EventHandlingConfiguration eventHandlingConfiguration,
                                                                      PlatformConnectionManager connectionManager,
                                                                      AxonServerConfiguration configuration) {
        return new EventProcessorInfoConfiguration(eventHandlingConfiguration, connectionManager, configuration);
    }

}

