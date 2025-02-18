/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.config.SpringConfigurer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

/**
 * Interceptor autoconfiguration class for Axon Framework application. Discovers {@link MessageHandlerInterceptor}s and {@link MessageDispatchInterceptor}
 * and registers them with the respective buses and gateways.
 * <p>
 * Note:
 * This class use a hack approach! Because some gateways/buses need an axonConfiguration to initialize, the usual way of
 * registering interceptors in the ConfigurerModule.onInitialize method does not work for the EventGateway.
 * This is due to a circular reference caused e.g. by JpaJavaxEventStoreAutoConfiguration.
 * So we register them by injecting gateway/bus components in to the InitializingBean function and register the interceptors there.
 *
 * @author Christian Thiel
 * @since 4.11.0
 */
@AutoConfiguration
@ConditionalOnClass(SpringConfigurer.class)
@AutoConfigureAfter({
        AxonAutoConfiguration.class,
        JpaAutoConfiguration.class,
        JpaEventStoreAutoConfiguration.class,
        NoOpTransactionAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
public class InterceptorAutoConfiguration {

    @Bean
    @ConditionalOnBean(MessageDispatchInterceptor.class)
    public InitializingBean commandDispatchInterceptorConfigurer(
            CommandGateway commandGateway,
            Optional<List<MessageDispatchInterceptor<? super CommandMessage<?>>>> interceptors
    ) {
        return () -> interceptors.ifPresent(it -> it.forEach(commandGateway::registerDispatchInterceptor));
    }

    @Bean
    @ConditionalOnBean(MessageDispatchInterceptor.class)
    public InitializingBean eventDispatchInterceptorConfigurer(
            EventGateway eventGateway,
            Optional<List<MessageDispatchInterceptor<? super EventMessage<?>>>> interceptors
    ) {
        return () -> interceptors.ifPresent(it -> it.forEach(eventGateway::registerDispatchInterceptor));
    }

    @Bean
    @ConditionalOnBean(MessageDispatchInterceptor.class)
    public InitializingBean queryDispatchInterceptorConfigurer(
            QueryGateway queryGateway,
            Optional<List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>>> interceptors
    ) {
        return () -> interceptors.ifPresent(it -> it.forEach(queryGateway::registerDispatchInterceptor));
    }

    @Bean
    @ConditionalOnBean(MessageHandlerInterceptor.class)
    public InitializingBean commandHandlerInterceptorConfigurer(
            CommandBus commandBus,
            Optional<List<MessageHandlerInterceptor<? super CommandMessage<?>>>> interceptors
    ) {
        return () -> interceptors.ifPresent(it -> it.forEach(commandBus::registerHandlerInterceptor));
    }

    @Bean
    @ConditionalOnBean(MessageHandlerInterceptor.class)
    public InitializingBean queryHandlerInterceptorConfigurer(
            QueryBus queryBus,
            Optional<List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>>> interceptors) {
        return () -> interceptors.ifPresent(it -> it.forEach(queryBus::registerHandlerInterceptor));
    }

    @Bean
    public ConfigurerModule messageHandlerInterceptorConfigurer(
            Optional<List<MessageHandlerInterceptor<? super EventMessage<?>>>> interceptors
    ) {
        return configurer -> interceptors
                .ifPresent(it -> it
                        .forEach(i -> configurer.eventProcessing().registerDefaultHandlerInterceptor((c, n) -> i)
                        )
                );
    }
}
