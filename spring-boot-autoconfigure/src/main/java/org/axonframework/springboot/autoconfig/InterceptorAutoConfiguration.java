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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.config.SpringConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.List;

/**
 * Interceptor autoconfiguration class for Axon Framework application. Discovers {@link MessageHandlerInterceptor}s and {@link MessageDispatchInterceptor}
 * and registers them with the respective buses and gateways.
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

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void commandDispatchInterceptorConfigurer(CommandGateway commandGateway, List<MessageDispatchInterceptor<? super CommandMessage<?>>> interceptors) {
        interceptors.forEach(i -> commandGateway.registerDispatchInterceptor(i));
    }

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void eventDispatchInterceptorConfigurer(EventGateway eventGateway, List<MessageDispatchInterceptor<? super EventMessage<?>>> interceptors) {
        interceptors.forEach(i -> eventGateway.registerDispatchInterceptor(i));
    }

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void queryDispatchInterceptorConfigurer(QueryGateway queryGateway, List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> interceptors) {
        interceptors.forEach(i -> queryGateway.registerDispatchInterceptor(i));
    }

    @ConditionalOnClass(MessageHandlerInterceptor.class)
    @Autowired
    public void commandHandlerInterceptorConfigurer(CommandBus commandBus, List<MessageHandlerInterceptor<? super CommandMessage<?>>> interceptors) {
        interceptors.forEach(i -> commandBus.registerHandlerInterceptor(i));
    }

    @ConditionalOnClass(MessageHandlerInterceptor.class)
    @Autowired
    public void queryHandlerInterceptorConfigurer(QueryBus queryBus, List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> interceptors) {
        interceptors.forEach(i -> queryBus.registerHandlerInterceptor(i));
    }
}
