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

package org.axonframework.extension.reactor.messaging.core.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.DefaultReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactiveCommandGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.DefaultReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactiveEventGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.DefaultReactiveQueryGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactiveQueryGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

/**
 * A {@link ConfigurationEnhancer} registering the default reactive gateway components for the Reactor extension.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link DefaultReactiveCommandGateway} for class {@link ReactiveCommandGateway}</li>
 *     <li>Registers a {@link DefaultReactiveEventGateway} for class {@link ReactiveEventGateway}</li>
 *     <li>Registers a {@link DefaultReactiveQueryGateway} for class {@link ReactiveQueryGateway}</li>
 * </ul>
 * <p>
 * The order is set to {@link Integer#MAX_VALUE} (same as
 * {@link org.axonframework.messaging.core.configuration.MessagingConfigurationDefaults}) since this enhancer provides
 * defaults that should be overridable by user-registered components.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorConfigurer
 */
public class ReactorConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer compared to others, equal to {@link Integer#MAX_VALUE}.
     */
    public static final int ENHANCER_ORDER = Integer.MAX_VALUE;

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(ReactiveCommandGateway.class, this::defaultReactiveCommandGateway)
                .registerIfNotPresent(ReactiveEventGateway.class, this::defaultReactiveEventGateway)
                .registerIfNotPresent(ReactiveQueryGateway.class, this::defaultReactiveQueryGateway);
    }

    private ReactiveCommandGateway defaultReactiveCommandGateway(Configuration config) {
        return DefaultReactiveCommandGateway.builder()
                .commandGateway(config.getComponent(CommandGateway.class))
                .messageTypeResolver(config.getComponent(MessageTypeResolver.class))
                .build();
    }

    private ReactiveEventGateway defaultReactiveEventGateway(Configuration config) {
        return DefaultReactiveEventGateway.builder()
                .eventGateway(config.getComponent(EventGateway.class))
                .messageTypeResolver(config.getComponent(MessageTypeResolver.class))
                .build();
    }

    private ReactiveQueryGateway defaultReactiveQueryGateway(Configuration config) {
        return DefaultReactiveQueryGateway.builder()
                .queryGateway(config.getComponent(QueryGateway.class))
                .messageTypeResolver(config.getComponent(MessageTypeResolver.class))
                .build();
    }
}
