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

package io.axoniq.axonhub.client.boot;


import io.axoniq.axonhub.client.AxonIQPlatformConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.command.AxonIQCommandBus;
import io.axoniq.axonhub.client.command.CommandPriorityCalculator;
import io.axoniq.axonhub.client.query.AxonIQQueryBus;
import io.axoniq.axonhub.client.query.QueryPriorityCalculator;
import org.axonframework.boot.autoconfig.AxonAutoConfiguration;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
public class MessagingAutoConfiguration {
    @Bean
    public AxonIQPlatformConfiguration routingConfiguration() {
        return new AxonIQPlatformConfiguration();
    }

    @Bean
    public PlatformConnectionManager platformConnectionManager(AxonIQPlatformConfiguration routingConfiguration) {
        return new PlatformConnectionManager(routingConfiguration);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public CommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration , AxonIQPlatformConfiguration routingConfiguration,
                                 Serializer serializer, PlatformConnectionManager platformConnectionManager, CommandPriorityCalculator priorityCalculator) {

        SimpleCommandBus commandBus = new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));
        commandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));

        return new AxonIQCommandBus(platformConnectionManager, routingConfiguration, commandBus, serializer, new AnnotationRoutingStrategy(),
                priorityCalculator);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandPriorityCalculator commandPriorityCalculator() {
        return new CommandPriorityCalculator() {};
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryPriorityCalculator queryPriorityCalculator() {
        return new QueryPriorityCalculator() {};
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryInvocationErrorHandler queryInvocationErrorHandler() {
        return new LoggingQueryInvocationErrorHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryBus queryBus(PlatformConnectionManager platformConnectionManager, AxonIQPlatformConfiguration routingConfiguration,
                             AxonConfiguration axonConfiguration,  TransactionManager txManager, Serializer serializer,
                             QueryPriorityCalculator priorityCalculator, QueryInvocationErrorHandler queryInvocationErrorHandler) {
        return new AxonIQQueryBus(platformConnectionManager, routingConfiguration,
                new SimpleQueryBus(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"), txManager, queryInvocationErrorHandler),
                serializer, priorityCalculator);
    }

}

