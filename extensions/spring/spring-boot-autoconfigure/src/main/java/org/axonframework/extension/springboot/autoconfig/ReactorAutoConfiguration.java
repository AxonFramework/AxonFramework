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

import org.axonframework.extension.reactor.messaging.commandhandling.gateway.DefaultReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.DefaultReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.core.ReactiveMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.DefaultReactiveQueryGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactiveQueryGateway;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * {@link AutoConfiguration} for the Axon Framework Reactor extension.
 * <p>
 * Registers default reactive gateway beans when Project Reactor is on the classpath and no user-defined beans exist.
 * This autoconfiguration creates:
 * <ul>
 *     <li>{@link ReactiveCommandGateway} via {@link DefaultReactiveCommandGateway}</li>
 *     <li>{@link ReactiveEventGateway} via {@link DefaultReactiveEventGateway}</li>
 *     <li>{@link ReactiveQueryGateway} via {@link DefaultReactiveQueryGateway}</li>
 * </ul>
 * <p>
 * Each gateway automatically picks up all {@link ReactiveMessageDispatchInterceptor} beans of the matching message
 * type from the Spring application context. For example, any
 * {@code ReactiveMessageDispatchInterceptor<CommandMessage>} bean will be registered on the
 * {@link ReactiveCommandGateway}.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "reactor.core.publisher.Mono")
public class ReactorAutoConfiguration {

    /**
     * Creates a {@link ReactiveCommandGateway} if none is already defined.
     * <p>
     * Automatically registers all {@link ReactiveMessageDispatchInterceptor} beans for {@link CommandMessage}s found
     * in the application context.
     *
     * @param commandGateway      The {@link CommandGateway} to delegate command dispatching to.
     * @param messageTypeResolver The {@link MessageTypeResolver} for resolving message types.
     * @param interceptors        All {@link ReactiveMessageDispatchInterceptor} beans for {@link CommandMessage}s.
     * @return A {@link DefaultReactiveCommandGateway} instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveCommandGateway reactiveCommandGateway(
            CommandGateway commandGateway,
            MessageTypeResolver messageTypeResolver,
            List<ReactiveMessageDispatchInterceptor<CommandMessage>> interceptors
    ) {
        return DefaultReactiveCommandGateway.builder()
                .commandGateway(commandGateway)
                .messageTypeResolver(messageTypeResolver)
                .dispatchInterceptors(interceptors)
                .build();
    }

    /**
     * Creates a {@link ReactiveEventGateway} if none is already defined.
     * <p>
     * Automatically registers all {@link ReactiveMessageDispatchInterceptor} beans for {@link EventMessage}s found in
     * the application context.
     *
     * @param eventGateway        The {@link EventGateway} to delegate event publishing to.
     * @param messageTypeResolver The {@link MessageTypeResolver} for resolving message types.
     * @param interceptors        All {@link ReactiveMessageDispatchInterceptor} beans for {@link EventMessage}s.
     * @return A {@link DefaultReactiveEventGateway} instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveEventGateway reactiveEventGateway(
            EventGateway eventGateway,
            MessageTypeResolver messageTypeResolver,
            List<ReactiveMessageDispatchInterceptor<EventMessage>> interceptors
    ) {
        return DefaultReactiveEventGateway.builder()
                .eventGateway(eventGateway)
                .messageTypeResolver(messageTypeResolver)
                .dispatchInterceptors(interceptors)
                .build();
    }

    /**
     * Creates a {@link ReactiveQueryGateway} if none is already defined.
     * <p>
     * Automatically registers all {@link ReactiveMessageDispatchInterceptor} beans for {@link QueryMessage}s found in
     * the application context.
     *
     * @param queryGateway        The {@link QueryGateway} to delegate query dispatching to.
     * @param messageTypeResolver The {@link MessageTypeResolver} for resolving message types.
     * @param interceptors        All {@link ReactiveMessageDispatchInterceptor} beans for {@link QueryMessage}s.
     * @return A {@link DefaultReactiveQueryGateway} instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveQueryGateway reactiveQueryGateway(
            QueryGateway queryGateway,
            MessageTypeResolver messageTypeResolver,
            List<ReactiveMessageDispatchInterceptor<QueryMessage>> interceptors
    ) {
        return DefaultReactiveQueryGateway.builder()
                .queryGateway(queryGateway)
                .messageTypeResolver(messageTypeResolver)
                .dispatchInterceptors(interceptors)
                .build();
    }
}
