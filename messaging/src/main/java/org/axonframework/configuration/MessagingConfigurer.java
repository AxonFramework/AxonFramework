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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The messaging {@link NewConfigurer} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for {@link #registerCommandBus(ComponentFactory) command},
 * {@link #registerEventSink(ComponentFactory) evnet}, and {@link #registerQueryBus(ComponentFactory) query}
 * infrastructure components.
 * <p>
 * This configurer registers the following defaults:
 * <ul>
 *     <li>Registers a {@link org.axonframework.messaging.ClassBasedMessageTypeResolver} as the {@link org.axonframework.messaging.MessageTypeResolver}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.gateway.DefaultCommandGateway} as the {@link org.axonframework.commandhandling.gateway.CommandGateway}</li>
 *     <li>Registers a {@link org.axonframework.commandhandling.SimpleCommandBus} as the {@link CommandBus}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.gateway.DefaultEventGateway} as the {@link org.axonframework.eventhandling.gateway.EventGateway}</li>
 *     <li>Registers a TODO as the {@link EventSink}</li>
 *     <li>Registers a {@link org.axonframework.eventhandling.SimpleEventBus} as the {@link org.axonframework.eventhandling.EventBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.DefaultQueryGateway} as the {@link org.axonframework.queryhandling.QueryGateway}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryBus} as the {@link QueryBus}</li>
 *     <li>Registers a {@link org.axonframework.queryhandling.SimpleQueryUpdateEmitter} as the {@link QueryUpdateEmitter}</li>
 * </ul>
 * To replace or decorate any of these defaults, use their respective interfaces as the identifier. For example, to
 * adjust the {@code CommandBus}, invoke {@link #registerComponent(Class, ComponentFactory)} with
 * {@code CommandBus.class} to replace it.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class MessagingConfigurer
        extends DelegatingConfigurer<MessagingConfigurer>
        implements ApplicationConfigurer<MessagingConfigurer> {

    /**
     * Build a default {@code MessagingConfigurer} instance with several messaging defaults, as well as methods to
     * register (e.g.) a {@link #registerCommandBus(ComponentFactory) command bus}.
     * <p>
     * Besides the specific operations, the {@code MessagingConfigurer} allows for configuring generic
     * {@link Component components}, {@link ComponentDecorator component decorators},
     * {@link ConfigurationEnhancer enhancers}, and {@link Module modules} for a message-driven application.
     *
     * @return A {@code MessagingConfigurer} instance for further configuring.
     */
    public static MessagingConfigurer create() {
        return new MessagingConfigurer(AxonApplication.create())
                .registerEnhancer(new MessagingConfigurationDefaults());
    }

    /**
     * Construct a {@code ModellingConfigurer} using the given {@code delegate} to delegate all registry-specific
     * operations to.
     * <p>
     * It is recommended to use the {@link #create()} method in most cases instead of this constructor.
     *
     * @param delegate The delegate {@code AxonApplication} the {@code MessagingConfigurer} is based on.
     */
    public MessagingConfigurer(@Nonnull AxonApplication delegate) {
        super(delegate);
    }

    /**
     * Registers the given {@link CommandBus} factory in this {@code Configurer}.
     * <p>
     * The {@code commandBusFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link CommandBus} instance.
     *
     * @param commandBusFactory The factory building the {@link CommandBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerCommandBus(@Nonnull ComponentFactory<CommandBus> commandBusFactory) {
        return registerComponent(CommandBus.class, commandBusFactory);
    }

    /**
     * Registers the given {@link EventSink} factory in this {@code Configurer}.
     * <p>
     * The {@code eventSinkFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link EventSink} instance.
     *
     * @param eventSinkFactory The factory building the {@link EventSink}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerEventSink(@Nonnull ComponentFactory<EventSink> eventSinkFactory) {
        return registerComponent(EventSink.class, eventSinkFactory);
    }

    /**
     * Registers the given {@link QueryBus} factory in this {@code Configurer}.
     * <p>
     * The {@code queryBusFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link QueryBus} instance.
     *
     * @param queryBusFactory The factory building the {@link QueryBus}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryBus(@Nonnull ComponentFactory<QueryBus> queryBusFactory) {
        return registerComponent(QueryBus.class, queryBusFactory);
    }

    /**
     * Registers the given {@link QueryUpdateEmitter} factory in this {@code Configurer}.
     * <p>
     * The {@code queryUpdateEmitterFactory} receives the {@link NewConfiguration} as input and is expected to return a
     * {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterFactory The factory building the {@link QueryUpdateEmitter}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer registerQueryUpdateEmitter(
            @Nonnull ComponentFactory<QueryUpdateEmitter> queryUpdateEmitterFactory
    ) {
        return registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterFactory);
    }

    /**
     * Delegates the given {@code configureTask} to the {@link AxonApplication} this {@code MessagingConfigurer}
     * delegates to.
     * <p>
     * Use this operation to invoke registration methods that only exist on the {@code AxonApplication}.
     *
     * @param configureTask Lambda consuming the delegate {@link AxonApplication}.
     * @return The current instance of the {@code Configurer} for a fluent API.
     */
    public MessagingConfigurer application(@Nonnull Consumer<AxonApplication> configureTask) {
        return delegate(AxonApplication.class, configureTask);
    }

    /**
     * {@link #build() Builds the configuration} and starts it immediately.
     * <p>
     * It is not recommended to change any configuration on {@code this StartableConfigurer} once this method is
     * called.
     *
     * @return The fully initialized and started {@link AxonConfiguration}.
     */
    @Override
    public AxonConfiguration start() {
        AtomicReference<AxonApplication> axonReference = new AtomicReference<>();
        application(axonReference::set);
        return axonReference.get().start();
    }
}
