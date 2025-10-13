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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.configuration.MessagingConfigurationDefaults;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreBasedEventBus;
import org.axonframework.eventsourcing.eventstore.InterceptingEventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.List;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link EventSourcingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link AnnotationBasedTagResolver} for class {@link TagResolver}</li>
 *     <li>Registers a {@link InMemoryEventStorageEngine} for class {@link EventStorageEngine}</li>
 *     <li>Registers a {@link SimpleEventStore} for class {@link EventStore}</li>
 * </ul>
 * Furthermore, this enhancer will decorate the:
 * <ul>
 *     <li>The {@link EventStore} in a {@link InterceptingEventStore} <b>if</b> there are any
 *     {@link MessageDispatchInterceptor MessageDispatchInterceptors} present in the {@link DispatchInterceptorRegistry}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class EventSourcingConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer compared to others, equal to 100 positions before
     * {@link MessagingConfigurationDefaults} (thus, {@link MessagingConfigurationDefaults#ENHANCER_ORDER} - 100).
     */
    public static final int ENHANCER_ORDER = MessagingConfigurationDefaults.ENHANCER_ORDER - 100;

    private boolean isSpringContext = false;

    public EventSourcingConfigurationDefaults() {
        this.isSpringContext = true;
    }

    public EventSourcingConfigurationDefaults(boolean isSpringContext) {
        this.isSpringContext = isSpringContext;
    }

    @Override
    public int order() {
        return ENHANCER_ORDER;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        // Register components
        registry.registerIfNotPresent(TagResolver.class, EventSourcingConfigurationDefaults::defaultTagResolver)
                .registerIfNotPresent(EventStorageEngine.class,
                                      EventSourcingConfigurationDefaults::defaultEventStorageEngine)
                .registerIfNotPresent(EventStore.class, EventSourcingConfigurationDefaults::simpleEventStore);
        registry.registerDecorator(
                EventStore.class,
                InterceptingEventStore.DECORATION_ORDER,
                (config, name, delegate) -> {
                    List<MessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors =
                            config.getComponent(DispatchInterceptorRegistry.class).eventInterceptors(config);
                    return dispatchInterceptors.isEmpty()
                            ? delegate
                            : new InterceptingEventStore(delegate, dispatchInterceptors);
                }
        );
        registry.registerDecorator(
                EventStore.class,
                InterceptingEventStore.DECORATION_ORDER + 50,
                (config, name, delegate) -> new EventStoreBasedEventBus(
                        delegate,
                        new SimpleEventBus(config.getComponent(UnitOfWorkFactory.class)))
        );

        registry.registerComponent(EventBus.class,
                                   c -> c.getOptionalComponent(EventStore.class).filter(it -> it instanceof EventBus)
                                         .map(it -> (EventBus) it).orElseThrow(() -> new IllegalStateException(
                                                   "The EventStore is not an EventBus, so the EventBus must be configured explicitly.")));

        if (isSpringContext) { // fixme: due to difference in SpringComponentRegistry and DefaultComponentRegistry
            registry.registerIfNotPresent(
                    EventSink.class,
                    cfg -> cfg.getComponent(EventStore.class));
        } else {
            registry.registerComponent(
                    EventSink.class,
                    cfg -> cfg.getComponent(EventStore.class));
        }
    }

    private static TagResolver defaultTagResolver(Configuration configuration) {
        return new AnnotationBasedTagResolver();
    }

    private static EventStorageEngine defaultEventStorageEngine(Configuration config) {
        return new InMemoryEventStorageEngine();
    }

    private static SimpleEventStore simpleEventStore(Configuration config) {
        return new SimpleEventStore(config.getComponent(EventStorageEngine.class),
                                    config.getComponent(TagResolver.class));
    }
}
