/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.MessagingConfigurationDefaults;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.InterceptingEventBus;
import org.axonframework.eventhandling.InterceptingEventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.List;

/**
 * A {@link ConfigurationEnhancer} registering the default components for event publishing and event bus.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link SimpleEventBus} for class {@link EventBus}</li>
 *     <li>Registers a default {@link EventSink} wrapping the {@link EventBus}</li>
 * </ul>
 * <p>
 * Furthermore, this enhancer will decorate the:
 * <ul>
 *     <li>The {@link EventSink} in a {@link InterceptingEventSink} <b>if</b> there are any
 *     {@link MessageDispatchInterceptor MessageDispatchInterceptors} present in the {@link DispatchInterceptorRegistry}.</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class EventBusConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer compared to others, equal to 100 positions before
     * {@link MessagingConfigurationDefaults} (thus, {@link MessagingConfigurationDefaults#ENHANCER_ORDER} - 100).
     */
    public static final int ENHANCER_ORDER = MessagingConfigurationDefaults.ENHANCER_ORDER;

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registerComponents(registry);
        registerDecorators(registry);
    }

    private static void registerComponents(@Nonnull ComponentRegistry registry) {
        registry.registerIfNotPresent(EventBus.class, EventBusConfigurationDefaults::defaultEventBus);
    }

    private static EventBus defaultEventBus(Configuration config) {
        return new SimpleEventBus(config.getComponent(UnitOfWorkFactory.class));
    }

    private static void registerDecorators(@Nonnull ComponentRegistry registry) {
        registry.registerDecorator(
                EventBus.class,
                InterceptingEventBus.DECORATION_ORDER,
                (config, name, delegate) -> {
                    List<MessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors =
                            config.getComponent(DispatchInterceptorRegistry.class).eventInterceptors(config);
                    return dispatchInterceptors.isEmpty()
                            ? delegate
                            : new InterceptingEventBus(delegate, dispatchInterceptors);
                }
        );
    }
}
