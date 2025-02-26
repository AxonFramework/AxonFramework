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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.configuration.MessageHandlingComponent;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;

import javax.annotation.Nonnull;

/**
 * The infrastructure {@code Module} of Axon Framework's configuration API.
 * <p>
 * Provides register operations for command, event, and query infrastructure components.
 * <p>
 * Use the {@link #configurer(LifecycleSupportingConfiguration)} operation from within
 * {@link RootConfigurer#registerModule(ModuleBuilder)} to start your application's infrastructure configuration.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface InfraConfigurer extends Module {

    /**
     * Returns an {@code InfraConfigurer} instance with default components configured, such as a
     * {@link SimpleCommandBus} and {@link SimpleEventBus}.
     *
     * @return An {@code InfraConfigurer} instance for further configuration.
     */
    static InfraConfigurer configurer(LifecycleSupportingConfiguration config) {
        return new DefaultInfraConfigurer(config);
    }

    /**
     * Configures the given Command Bus to use in this configuration. The builder receives the Configuration as input
     * and is expected to return a fully initialized {@link CommandBus} instance.
     *
     * @param commandBusBuilder The builder function for the {@link CommandBus}
     * @return The current instance of the {@link NewConfigurer}, for chaining purposes.
     */
    default InfraConfigurer registerCommandBus(@Nonnull ComponentBuilder<CommandBus> commandBusBuilder) {
        return (InfraConfigurer) registerComponent(CommandBus.class, commandBusBuilder);
    }

    /**
     * Registers a command handler bean with this {@link NewConfigurer}. The bean may be of any type. The actual command
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link CommandHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully
     * initialized instance of the command handler bean.
     *
     * @param commandHandlerBuilder the builder function of the command handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    InfraConfigurer registerCommandHandler(@Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder);

    /**
     * Configures the given Event Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link EventBus} instance.
     * <p>
     * Note that this builder should not be used when an Event Store is configured. Since Axon 3, the Event Store will
     * act as Event Bus implementation as well.
     *
     * @param eventBusBuilder The builder function for the {@link EventBus}
     * @return The current instance of the {@link NewConfigurer}, for chaining purposes.
     */
    default InfraConfigurer registerEventBus(@Nonnull ComponentBuilder<EventBus> eventBusBuilder) {
        return (InfraConfigurer) registerComponent(EventBus.class, eventBusBuilder);
    }

    /**
     * Configures the given Query Bus to use in this configuration. The builder receives the Configuration as input and
     * is expected to return a fully initialized {@link QueryBus} instance.
     *
     * @param queryBusBuilder The builder function for the {@link QueryBus}
     * @return The current instance of the {@link NewConfigurer}, for chaining purposes.
     */
    default InfraConfigurer registerQueryBus(@Nonnull ComponentBuilder<QueryBus> queryBusBuilder) {
        return (InfraConfigurer) registerComponent(QueryBus.class, queryBusBuilder);
    }

    /**
     * Configures the given Query Update Emitter to use in this configuration. The builder receives the Configuration as
     * input and is expected to return a fully initialized {@link QueryUpdateEmitter} instance.
     *
     * @param queryUpdateEmitterBuilder The builder function for the {@link QueryUpdateEmitter}
     * @return The current instance of the {@link NewConfigurer}, for chaining purposes.
     */
    default InfraConfigurer registerQueryUpdateEmitter(
            @Nonnull ComponentBuilder<QueryUpdateEmitter> queryUpdateEmitterBuilder
    ) {
        return (InfraConfigurer) registerComponent(QueryUpdateEmitter.class, queryUpdateEmitterBuilder);
    }

    /**
     * Registers a query handler bean with this {@link NewConfigurer}. The bean may be of any type. The actual query
     * handler methods will be detected based on the annotations present on the bean's methods. Message handling
     * functions annotated with {@link QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully
     * initialized instance of the query handler bean.
     *
     * @param queryHandlerBuilder the builder function of the query handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    InfraConfigurer registerQueryHandler(@Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder);

    /**
     * Registers a message handler bean with this configuration. The bean may be of any type. The actual message handler
     * methods will be detected based on the annotations present on the bean's methods. Message handling functions
     * annotated with {@link CommandHandler}, {@link org.axonframework.eventhandling.EventHandler} and
     * {@link QueryHandler} will be taken into account.
     * <p>
     * The builder function receives the {@link NewConfiguration} as input, and is expected to return a fully
     * initialized instance of the message handler bean.
     *
     * @param handlingComponentBuilder the builder function of the message handler bean
     * @return the current instance of the {@link NewConfigurer}, for chaining purposes
     */
    InfraConfigurer registerMessageHandlingComponent(
            @Nonnull ComponentBuilder<MessageHandlingComponent> handlingComponentBuilder
    );
}
