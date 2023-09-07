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
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.tracing.SpanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configures Axon Server implementations of the {@link CommandBus}, {@link EventStore}, and {@link QueryBus}.
 *
 * @author Marc Gathier
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@AutoConfiguration
@AutoConfigureAfter(AxonServerAutoConfiguration.class)
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(AxonServerConfiguration.class)
@ConditionalOnProperty(name = "axon.axonserver.enabled", matchIfMissing = true)
public class AxonServerBusAutoConfiguration {

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
                                                     TargetContextResolver<? super CommandMessage<?>> targetContextResolver,
                                                     CommandBusSpanFactory spanFactory) {
        return AxonServerCommandBus.builder()
                                   .axonServerConnectionManager(axonServerConnectionManager)
                                   .configuration(axonServerConfiguration)
                                   .localSegment(localSegment)
                                   .serializer(messageSerializer)
                                   .routingStrategy(routingStrategy)
                                   .priorityCalculator(priorityCalculator)
                                   .loadFactorProvider(loadFactorProvider)
                                   .targetContextResolver(targetContextResolver)
                                   .spanFactory(spanFactory)
                                   .build();
    }

    @Bean
    @ConditionalOnMissingBean(QueryBus.class)
    public AxonServerQueryBus queryBus(AxonServerConnectionManager axonServerConnectionManager,
                                       AxonServerConfiguration axonServerConfiguration,
                                       org.axonframework.config.Configuration axonConfiguration,
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
                              .spanFactory(axonConfiguration.getComponent(QueryBusSpanFactory.class))
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
                                 .spanFactory(axonConfiguration.getComponent(QueryBusSpanFactory.class))
                                 .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventStore eventStore(AxonServerConfiguration axonServerConfiguration,
                                 org.axonframework.config.Configuration configuration,
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
                                   .spanFactory(configuration.getComponent(EventBusSpanFactory.class))
                                   .build();
    }
}

