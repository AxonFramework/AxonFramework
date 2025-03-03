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

import javax.annotation.Nonnull;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.config.CommandBusBuilder;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.configuration.MessageHandlingComponent;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;

/**
 * Default implementation of the {@link InfraConfigurer}.
 * <p>
 * Note that this configurer implementation is not thread-safe.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultInfraConfigurer extends AbstractConfigurer<InfraConfigurer> implements InfraConfigurer {

    /**
     * Initialize the {@code DefaultInfraConfigurer}.
     */
    protected DefaultInfraConfigurer(LifecycleSupportingConfiguration config) {
        super(config);
        super.registerComponent(CommandBus.class, this::defaultCommandBus);
        super.registerComponent(CommandGateway.class, this::defaultCommandGateway);
        super.registerComponent(EventBus.class, this::defaultEventBus);
        super.registerComponent(EventGateway.class, this::defaultEventGateway);
        super.registerComponent(QueryBus.class, this::defaultQueryBus);
        super.registerComponent(QueryGateway.class, this::defaultQueryGateway);
        super.registerComponent(QueryUpdateEmitter.class, this::defaultQueryUpdateEmitter);
    }

    /**
     * Provides the default CommandBus implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default CommandBus to use.
     */
    protected CommandBus defaultCommandBus(NewConfiguration config) {
        return config.getOptionalComponent(CommandBus.class)
                     .orElseGet(() -> {
                         CommandBusBuilder commandBusBuilder = CommandBusBuilder.forSimpleCommandBus();
                         config.getOptionalComponent(TransactionManager.class)
                               .ifPresent(commandBusBuilder::withTransactions);
//                    if (!config.correlationDataProviders().isEmpty()) {
//                        CorrelationDataInterceptor<Message<?>> interceptor = new CorrelationDataInterceptor<>(config.correlationDataProviders());
//                        commandBusBuilder.withHandlerInterceptor(interceptor);
//                        //TODO - commandBusBuilder.withDispatchInterceptor(interceptor);
//                    }
                         return commandBusBuilder.build(config);
                     });
    }

    /**
     * Returns a {@link DefaultCommandGateway} that will use the configuration's {@link CommandBus} to dispatch
     * commands.
     *
     * @param config The configuration that supplies the command bus.
     * @return The default command gateway.
     */
    protected CommandGateway defaultCommandGateway(NewConfiguration config) {
        return config.getOptionalComponent(CommandGateway.class)
                     .orElseGet(() -> new DefaultCommandGateway(
                             config.getComponent(CommandBus.class),
                             config.getComponent(MessageTypeResolver.class)
                     ));
    }

    /**
     * Provides the default EventBus implementation. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default EventBus to use.
     */
    protected EventBus defaultEventBus(NewConfiguration config) {
        return config.getOptionalComponent(EventBus.class)
                     .orElseGet(() -> SimpleEventBus.builder()
                                                    .spanFactory(config.getComponent(EventBusSpanFactory.class))
                                                    .build());
    }

    /**
     * Returns a {@link DefaultEventGateway} that will use the configuration's {@link EventBus} to publish events.
     *
     * @param config The configuration that supplies the event bus.
     * @return The default event gateway.
     */
    protected EventGateway defaultEventGateway(NewConfiguration config) {
        return config.getOptionalComponent(EventGateway.class)
                     .orElseGet(() -> DefaultEventGateway.builder()
                                                         .eventBus(config.getComponent(EventBus.class))
                                                         .build());
    }

    /**
     * Returns a {@link DefaultQueryGateway} that will use the configuration's {@link QueryBus} to dispatch queries.
     *
     * @param config The configuration that supplies the query bus.
     * @return The default query gateway.
     */
    protected QueryGateway defaultQueryGateway(NewConfiguration config) {
        return config.getOptionalComponent(QueryGateway.class)
                     .orElseGet(() -> DefaultQueryGateway.builder()
                                                         .queryBus(config.getComponent(QueryBus.class))
                                                         .build());
    }

    /**
     * Provides the default QueryBus implementations. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default QueryBus to use.
     */
    protected QueryBus defaultQueryBus(NewConfiguration config) {
        return config.getOptionalComponent(QueryBus.class)
                     .orElseGet(() -> SimpleQueryBus.builder()
                                                    .transactionManager(config.getComponent(
                                                            TransactionManager.class,
                                                            NoTransactionManager::instance
                                                    ))
                                                    .errorHandler(config.getComponent(
                                                            QueryInvocationErrorHandler.class,
                                                            () -> LoggingQueryInvocationErrorHandler.builder()
                                                                                                    .build()
                                                    ))
                                                    .queryUpdateEmitter(config.getComponent(QueryUpdateEmitter.class))
                                                    .spanFactory(config.getComponent(QueryBusSpanFactory.class))
                                                    .build());
    }

    /**
     * Provides the default QueryUpdateEmitter implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized
     * @return The default QueryUpdateEmitter to use
     */
    protected QueryUpdateEmitter defaultQueryUpdateEmitter(NewConfiguration config) {
        return config.getOptionalComponent(QueryUpdateEmitter.class)
                     .orElseGet(() -> SimpleQueryUpdateEmitter.builder()
                                                              .spanFactory(config.getComponent(
                                                                      QueryUpdateEmitterSpanFactory.class
                                                              ))
                                                              .build());
    }

    @Override
    public InfraConfigurer registerCommandHandler(@Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder) {
        // TODO #3067 - Implement in follow-up PR
        return this;
    }

    @Override
    public InfraConfigurer registerQueryHandler(@Nonnull ComponentBuilder<QueryHandler> queryHandlerBuilder) {
        // TODO #3067 - Implement in follow-up PR
        return this;
    }

    @Override
    public InfraConfigurer registerMessageHandlingComponent(
            @Nonnull ComponentBuilder<MessageHandlingComponent> handlingComponentBuilder
    ) {
        // TODO #3067 - Implement in follow-up PR
        return this;
    }
}
