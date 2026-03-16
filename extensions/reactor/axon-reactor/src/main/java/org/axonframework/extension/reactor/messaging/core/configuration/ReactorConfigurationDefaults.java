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

import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.DefaultReactorCommandGateway;
import org.axonframework.extension.reactor.messaging.commandhandling.gateway.ReactorCommandGateway;
import org.axonframework.extension.reactor.messaging.core.interception.DefaultReactorDispatchInterceptorRegistry;
import org.axonframework.extension.reactor.messaging.core.interception.ReactorDispatchInterceptorRegistry;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.DefaultReactorEventGateway;
import org.axonframework.extension.reactor.messaging.eventhandling.gateway.ReactorEventGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.DefaultReactorQueryGateway;
import org.axonframework.extension.reactor.messaging.queryhandling.gateway.ReactorQueryGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

/**
 * A {@link ConfigurationEnhancer} registering the default reactor components for the Reactor extension.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link DefaultReactorDispatchInterceptorRegistry} for class
 *         {@link ReactorDispatchInterceptorRegistry}</li>
 *     <li>Registers a {@link DefaultReactorCommandGateway} for class {@link ReactorCommandGateway}</li>
 *     <li>Registers a {@link DefaultReactorEventGateway} for class {@link ReactorEventGateway}</li>
 *     <li>Registers a {@link DefaultReactorQueryGateway} for class {@link ReactorQueryGateway}</li>
 * </ul>
 * <p>
 * The order is set to {@link Integer#MAX_VALUE} (same as
 * {@link org.axonframework.messaging.core.configuration.MessagingConfigurationDefaults}) since this enhancer provides
 * defaults that should be overridable by user-registered components.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
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
    public void enhance(ComponentRegistry registry) {
        registry.registerIfNotPresent(
                        ReactorDispatchInterceptorRegistry.class,
                        config -> new DefaultReactorDispatchInterceptorRegistry()
                )
                .registerIfNotPresent(ReactorCommandGateway.class, this::defaultReactorCommandGateway)
                .registerIfNotPresent(ReactorEventGateway.class, this::defaultReactorEventGateway)
                .registerIfNotPresent(ReactorQueryGateway.class, this::defaultReactorQueryGateway);
    }

    private ReactorCommandGateway defaultReactorCommandGateway(Configuration config) {
        return new DefaultReactorCommandGateway(
                config.getComponent(CommandGateway.class),
                config.getComponent(MessageTypeResolver.class),
                config.getComponent(ReactorDispatchInterceptorRegistry.class)
                      .commandInterceptors(config, ReactorCommandGateway.class, null)
        );
    }

    private ReactorEventGateway defaultReactorEventGateway(Configuration config) {
        return new DefaultReactorEventGateway(
                config.getComponent(EventGateway.class),
                config.getComponent(MessageTypeResolver.class),
                config.getComponent(ReactorDispatchInterceptorRegistry.class)
                      .eventInterceptors(config, ReactorEventGateway.class, null)
        );
    }

    private ReactorQueryGateway defaultReactorQueryGateway(Configuration config) {
        return new DefaultReactorQueryGateway(
                config.getComponent(QueryGateway.class),
                config.getComponent(MessageTypeResolver.class),
                config.getComponent(ReactorDispatchInterceptorRegistry.class)
                      .queryInterceptors(config, ReactorQueryGateway.class, null)
        );
    }
}
