/*
 * Copyright (c) 2010-2018. Axon Framework
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

/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

package org.axonframework.springboot.autoconfig;


import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.*;
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
 * Configures AxonServer as implementation for the CommandBus and QueryBus
 *
 * @author Marc Gathier
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
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

    @Bean(destroyMethod = "shutdown")
    public AxonServerConnectionManager platformConnectionManager(AxonServerConfiguration routingConfiguration) {
        return new AxonServerConnectionManager(routingConfiguration);
    }

    @Bean( destroyMethod = "disconnect")
    @Primary
    @ConditionalOnMissingBean(CommandBus.class)
    public AxonServerCommandBus commandBus(TransactionManager txManager,
                                           AxonConfiguration axonConfiguration,
                                           AxonServerConfiguration axonServerConfiguration,
                                           Serializer serializer,
                                           AxonServerConnectionManager axonServerConnectionManager,
                                           RoutingStrategy routingStrategy,
                                           CommandPriorityCalculator priorityCalculator) {

        SimpleCommandBus commandBus =
                SimpleCommandBus.builder()
                                .transactionManager(txManager)
                                .messageMonitor(axonConfiguration.messageMonitor(CommandBus.class, "commandBus"))
                                .build();

        commandBus.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
        );

        return new AxonServerCommandBus(axonServerConnectionManager,
                                        axonServerConfiguration,
                                        commandBus,
                                        serializer,
                                        routingStrategy,
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
        return LoggingQueryInvocationErrorHandler.builder().build();
    }

    @Bean( destroyMethod = "disconnect")
    @ConditionalOnMissingBean(QueryBus.class)
    public AxonServerQueryBus queryBus(AxonServerConnectionManager axonServerConnectionManager,
                                       AxonServerConfiguration axonServerConfiguration,
                                       AxonConfiguration axonConfiguration,
                                       TransactionManager txManager,
                                       @Qualifier("messageSerializer") Serializer messageSerializer,
                                       Serializer genericSerializer,
                                       QueryPriorityCalculator priorityCalculator,
                                       QueryInvocationErrorHandler queryInvocationErrorHandler) {
        SimpleQueryBus simpleQueryBus =
                SimpleQueryBus.builder()
                              .messageMonitor(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                              .transactionManager(txManager)
                              .queryUpdateEmitter(axonConfiguration.getComponent(QueryUpdateEmitter.class))
                              .errorHandler(queryInvocationErrorHandler)
                              .build();
        simpleQueryBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration
                                                                                           .correlationDataProviders()));

        return new AxonServerQueryBus(axonServerConnectionManager,
                                      axonServerConfiguration,
                                      simpleQueryBus.queryUpdateEmitter(),
                                      simpleQueryBus,
                                      messageSerializer,
                                      genericSerializer,
                                      priorityCalculator);
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
                                   .configuration(axonServerConfiguration)
                                   .platformConnectionManager(axonServerConnectionManager)
                                   .snapshotSerializer(snapshotSerializer)
                                   .eventSerializer(eventSerializer)
                                   .upcasterChain(configuration.upcasterChain())
                                   .build();
    }
}

