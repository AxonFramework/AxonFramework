/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.extension.reactor.messaging.core.interception.ReactorDispatchInterceptorRegistry;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

/**
 * {@link AutoConfiguration} for the Axon Framework Reactor extension.
 * <p>
 * Discovers {@link ReactorMessageDispatchInterceptor} beans from the Spring application context and registers them on
 * the {@link ReactorDispatchInterceptorRegistry}. The reactor gateways themselves are created by
 * {@link org.axonframework.extension.reactor.messaging.core.configuration.ReactorConfigurationDefaults
 * ReactorConfigurationDefaults}, which is registered as a
 * {@link org.axonframework.common.configuration.ConfigurationEnhancer ConfigurationEnhancer} via the service loader.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorDispatchInterceptorRegistry
 */
@AutoConfiguration
@AutoConfigureAfter(AxonAutoConfiguration.class)
@ConditionalOnClass(name = "reactor.core.publisher.Mono")
public class ReactorAutoConfiguration {

    /**
     * Creates a {@link DecoratorDefinition} that registers all discovered {@link ReactorMessageDispatchInterceptor}
     * beans on the {@link ReactorDispatchInterceptorRegistry}.
     *
     * @param interceptors        generic {@link Message} reactor dispatch interceptors
     * @param commandInterceptors {@link CommandMessage}-specific reactor dispatch interceptors
     * @param eventInterceptors   {@link EventMessage}-specific reactor dispatch interceptors
     * @param queryInterceptors   {@link QueryMessage}-specific reactor dispatch interceptors
     * @return a decorator definition that registers the discovered interceptors
     */
    @Bean
    @ConditionalOnBean(ReactorMessageDispatchInterceptor.class)
    public DecoratorDefinition<ReactorDispatchInterceptorRegistry, ReactorDispatchInterceptorRegistry>
    reactorDispatchInterceptorEnhancer(
            Optional<List<ReactorMessageDispatchInterceptor<Message>>> interceptors,
            Optional<List<ReactorMessageDispatchInterceptor<CommandMessage>>> commandInterceptors,
            Optional<List<ReactorMessageDispatchInterceptor<EventMessage>>> eventInterceptors,
            Optional<List<ReactorMessageDispatchInterceptor<QueryMessage>>> queryInterceptors
    ) {
        return DecoratorDefinition.forType(ReactorDispatchInterceptorRegistry.class)
                                  .with((config, name, delegate) -> registerInterceptors(
                                          delegate,
                                          interceptors,
                                          commandInterceptors,
                                          eventInterceptors,
                                          queryInterceptors
                                  ));
    }

    private static ReactorDispatchInterceptorRegistry registerInterceptors(
            ReactorDispatchInterceptorRegistry registry,
            Optional<List<ReactorMessageDispatchInterceptor<Message>>> interceptors,
            Optional<List<ReactorMessageDispatchInterceptor<CommandMessage>>> commandInterceptors,
            Optional<List<ReactorMessageDispatchInterceptor<EventMessage>>> eventInterceptors,
            Optional<List<ReactorMessageDispatchInterceptor<QueryMessage>>> queryInterceptors
    ) {
        interceptors.ifPresent(list -> {
            for (ReactorMessageDispatchInterceptor<Message> interceptor : list) {
                registry.registerInterceptor(interceptor);
            }
        });
        commandInterceptors.ifPresent(list -> {
            for (ReactorMessageDispatchInterceptor<CommandMessage> interceptor : list) {
                registry.registerCommandInterceptor(interceptor);
            }
        });
        eventInterceptors.ifPresent(list -> {
            for (ReactorMessageDispatchInterceptor<EventMessage> interceptor : list) {
                registry.registerEventInterceptor(interceptor);
            }
        });
        queryInterceptors.ifPresent(list -> {
            for (ReactorMessageDispatchInterceptor<QueryMessage> interceptor : list) {
                registry.registerQueryInterceptor(interceptor);
            }
        });
        return registry;
    }
}
