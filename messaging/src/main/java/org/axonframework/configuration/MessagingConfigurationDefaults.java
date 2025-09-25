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
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.RoutingStrategy;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotations.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.annotations.CommandDispatcherParameterResolverFactory;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.ConvertingCommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.InterceptingEventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.ConfigurationApplicationContext;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.configuration.reflection.ParameterResolverFactoryUtils;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.interceptors.DefaultDispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.DefaultHandlerInterceptorRegistry;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.HandlerInterceptorRegistry;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryPriorityCalculator;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterParameterResolverFactory;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link ConfigurationEnhancer} registering the default components of the {@link MessagingConfigurer}.
 * <p>
 * Will only register the following components <b>if</b> there is no component registered for the given class yet:
 * <ul>
 *     <li>Registers a {@link ClassBasedMessageTypeResolver} for class {@link MessageTypeResolver}</li>
 *     <li>Registers a {@link JacksonConverter} for class {@link Converter}</li>
 *     <li>Registers a {@link DelegatingMessageConverter} using the default {@link JacksonConverter}.</li>
 *     <li>Registers a {@link DelegatingEventConverter} using the default {@link JacksonConverter}.</li>
 *     <li>Registers a {@link DefaultCorrelationDataProviderRegistry} for class {@link CorrelationDataProviderRegistry} containing the {@link MessageOriginProvider}.</li>
 *     <li>Registers a {@link DefaultDispatchInterceptorRegistry} for class {@link DispatchInterceptorRegistry} containing an {@link CorrelationDataInterceptor} if there are {@link CorrelationDataProvider CorrelationDataProviders} present.</li>
 *     <li>Registers a {@link DefaultHandlerInterceptorRegistry} for class {@link HandlerInterceptorRegistry} containing an {@link CorrelationDataInterceptor} if there are {@link CorrelationDataProvider CorrelationDataProviders} present.</li>
 *     <li>Registers a {@link TransactionalUnitOfWorkFactory} for class {@link UnitOfWorkFactory}</li>
 *     <li>Registers a {@link SimpleCommandBus} for class {@link CommandBus}</li>
 *     <li>Registers a {@link CommandPriorityCalculator#defaultCalculator()} for class {@link CommandPriorityCalculator}</li>
 *     <li>Registers a {@link AnnotationRoutingStrategy} for class {@link RoutingStrategy}</li>
 *     <li>Registers a {@link DefaultCommandGateway} for class {@link CommandGateway}</li>
 *     <li>Registers a {@link DefaultEventGateway} for class {@link EventGateway}</li>
 *     <li>Registers a {@link SimpleEventBus} for class {@link EventBus}</li>
 *     <li>Registers a {@link SimpleQueryBus} for class {@link QueryBus}</li>
 *     <li>Registers a {@link SimpleQueryUpdateEmitter} for class {@link QueryUpdateEmitter}</li>
 *     <li>Registers a {@link QueryPriorityCalculator#defaultCalculator()} for class {@link QueryPriorityCalculator}</li>
 *     <li>Registers a {@link DefaultQueryGateway} for class {@link QueryGateway}</li>
 * </ul>
 * <p>
 * Furthermore, this enhancer will decorate the:
 * <ul>
 *     <li>The {@link CommandGateway} in a {@link ConvertingCommandGateway} with the present {@link MessageConverter}.</li>
 *     <li>The {@link CommandBus} in a {@link InterceptingCommandBus} <b>if</b> there are any
 *     {@link MessageDispatchInterceptor MessageDispatchInterceptors} present in the {@link DispatchInterceptorRegistry} or
 *     {@link MessageHandlerInterceptor MessageHandlerInterceptors} present in the {@link HandlerInterceptorRegistry}.</li>
 *     <li>The {@link EventSink} in a {@link InterceptingEventSink} <b>if</b> there are any
 *     {@link MessageDispatchInterceptor MessageDispatchInterceptors} present in the {@link DispatchInterceptorRegistry}.</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class MessagingConfigurationDefaults implements ConfigurationEnhancer {

    /**
     * The order of {@code this} enhancer compared to others, equal to {@link Integer#MAX_VALUE}.
     */
    public static final int ENHANCER_ORDER = Integer.MAX_VALUE;
    /**
     * The order in which the {@link ConvertingCommandGateway} is applied to the {@link CommandGateway} in the
     * {@link ComponentRegistry}. As such, any decorator with a lower value will be applied to the delegate, and any
     * higher value will be applied to the {@link ConvertingCommandGateway} itself. Using the same value can either lead
     * to application of the decorator to the delegate or the converting command gateway, depending on the order of
     * registration.
     * <p>
     * The order of the {@link ConvertingCommandGateway} is set to {@code Integer.MIN_VALUE + 100} to ensure it is
     * applied very early in the configuration process, but not the earliest to allow for other decorators to be
     * applied.
     */
    public static final int CONVERTING_COMMAND_GATEWAY_ORDER = Integer.MIN_VALUE + 100;

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
        registry.registerIfNotPresent(MessageTypeResolver.class,
                                      MessagingConfigurationDefaults::defaultMessageTypeResolver)
                .registerIfNotPresent(Converter.class, c -> new JacksonConverter())
                .registerIfNotPresent(MessageConverter.class, MessagingConfigurationDefaults::defaultMessageConverter)
                .registerIfNotPresent(EventConverter.class, MessagingConfigurationDefaults::defaultEventConverter)
                .registerIfNotPresent(UnitOfWorkFactory.class, MessagingConfigurationDefaults::defaultUnitOfWorkFactory)
                .registerIfNotPresent(CorrelationDataProviderRegistry.class,
                                      MessagingConfigurationDefaults::defaultCorrelationDataProviderRegistry)
                .registerIfNotPresent(DispatchInterceptorRegistry.class,
                                      MessagingConfigurationDefaults::defaultDispatchInterceptorRegistry)
                .registerIfNotPresent(HandlerInterceptorRegistry.class,
                                      MessagingConfigurationDefaults::defaultHandlerInterceptorRegistry)
                .registerIfNotPresent(CommandBus.class, MessagingConfigurationDefaults::defaultCommandBus)
                .registerIfNotPresent(CommandPriorityCalculator.class,
                                      c -> CommandPriorityCalculator.defaultCalculator())
                .registerIfNotPresent(RoutingStrategy.class, MessagingConfigurationDefaults::defaultRoutingStrategy)
                .registerIfNotPresent(CommandGateway.class, MessagingConfigurationDefaults::defaultCommandGateway)
                .registerIfNotPresent(EventGateway.class, MessagingConfigurationDefaults::defaultEventGateway)
                .registerIfNotPresent(EventSink.class, MessagingConfigurationDefaults::defaultEventSink)
                .registerIfNotPresent(EventBus.class, MessagingConfigurationDefaults::defaultEventBus)
                .registerIfNotPresent(QueryBus.class, MessagingConfigurationDefaults::defaultQueryBus)
                .registerIfNotPresent(QueryPriorityCalculator.class,
                                      c -> QueryPriorityCalculator.defaultCalculator())
                .registerIfNotPresent(QueryGateway.class, MessagingConfigurationDefaults::defaultQueryGateway);

        ParameterResolverFactoryUtils.registerToComponentRegistry(
                registry, config -> new QueryUpdateEmitterParameterResolverFactory()
        );
    }

    private static MessageTypeResolver defaultMessageTypeResolver(Configuration config) {
        return new ClassBasedMessageTypeResolver();
    }

    private static DelegatingMessageConverter defaultMessageConverter(Configuration c) {
        return new DelegatingMessageConverter(c.getComponent(Converter.class));
    }

    private static DelegatingEventConverter defaultEventConverter(Configuration c) {
        return c.getOptionalComponent(MessageConverter.class)
                .map(DelegatingEventConverter::new)
                .orElse(new DelegatingEventConverter(c.getComponent(Converter.class)));
    }

    private static UnitOfWorkFactory defaultUnitOfWorkFactory(Configuration config) {
        return new TransactionalUnitOfWorkFactory(
                config.getComponent(
                        TransactionManager.class,
                        NoTransactionManager::instance
                ),
                new SimpleUnitOfWorkFactory(new ConfigurationApplicationContext(config))
        );
    }

    private static CorrelationDataProviderRegistry defaultCorrelationDataProviderRegistry(Configuration config) {
        return new DefaultCorrelationDataProviderRegistry().registerProvider(c -> new MessageOriginProvider());
    }

    private static DispatchInterceptorRegistry defaultDispatchInterceptorRegistry(Configuration config) {
        CorrelationDataProviderRegistry providerRegistry = config.getComponent(CorrelationDataProviderRegistry.class);
        List<CorrelationDataProvider> providers = providerRegistry.correlationDataProviders(config);
        DispatchInterceptorRegistry dispatchInterceptorRegistry = new DefaultDispatchInterceptorRegistry();
        return providers.isEmpty()
                ? dispatchInterceptorRegistry
                : dispatchInterceptorRegistry.registerInterceptor(c -> new CorrelationDataInterceptor<>(providers));
    }

    private static HandlerInterceptorRegistry defaultHandlerInterceptorRegistry(Configuration config) {
        CorrelationDataProviderRegistry providerRegistry = config.getComponent(CorrelationDataProviderRegistry.class);
        List<CorrelationDataProvider> providers = providerRegistry.correlationDataProviders(config);
        DefaultHandlerInterceptorRegistry handlerInterceptorRegistry = new DefaultHandlerInterceptorRegistry();
        return providers.isEmpty()
                ? handlerInterceptorRegistry
                : handlerInterceptorRegistry.registerInterceptor(c -> new CorrelationDataInterceptor<>(providers));
    }

    private static CommandBus defaultCommandBus(Configuration config) {
        return new SimpleCommandBus(
                config.getComponent(UnitOfWorkFactory.class),
                config.getOptionalComponent(TransactionManager.class)
                      .map(tm -> (ProcessingLifecycleHandlerRegistrar) tm)
                      .map(List::of)
                      .orElse(Collections.emptyList())
        );
    }

    private static RoutingStrategy defaultRoutingStrategy(Configuration config) {
        return new AnnotationRoutingStrategy();
    }

    private static CommandGateway defaultCommandGateway(Configuration config) {
        return new DefaultCommandGateway(
                config.getComponent(CommandBus.class),
                config.getComponent(MessageTypeResolver.class),
                config.getComponent(CommandPriorityCalculator.class),
                config.getComponent(RoutingStrategy.class)
        );
    }

    private static EventBus defaultEventBus(Configuration config) {
        return SimpleEventBus.builder()
                             .build();
    }

    // TODO #3392 - Replace for actual EventSink implementation.
    private static EventSink defaultEventSink(Configuration config) {
        EventBus eventBus = config.getComponent(EventBus.class);
        return new EventSink() {
            @Override
            public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                                   @Nonnull List<EventMessage> events) {
                eventBus.publish(events);
                return FutureUtils.emptyCompletedFuture();
            }

            @Override
            public void describeTo(@Nonnull ComponentDescriptor descriptor) {
                // Not important
            }
        };
    }

    private static EventGateway defaultEventGateway(Configuration config) {
        return new DefaultEventGateway(
                config.getComponent(EventSink.class),
                config.getComponent(MessageTypeResolver.class)
        );
    }

    private static QueryGateway defaultQueryGateway(Configuration config) {
        return new DefaultQueryGateway(
                config.getComponent(QueryBus.class),
                config.getComponent(MessageTypeResolver.class),
                config.getComponent(QueryPriorityCalculator.class)
        );
    }

    private static QueryBus defaultQueryBus(Configuration config) {
        return new SimpleQueryBus(config.getComponent(UnitOfWorkFactory.class));
    }

    private static void registerDecorators(@Nonnull ComponentRegistry registry) {
        registry.registerDecorator(
                CommandGateway.class,
                CONVERTING_COMMAND_GATEWAY_ORDER,
                (config, name, delegate) -> new ConvertingCommandGateway(
                        delegate,
                        config.getComponent(MessageConverter.class)
                )
        );
        registry.registerDecorator(
                CommandBus.class,
                InterceptingCommandBus.DECORATION_ORDER,
                (config, name, delegate) -> {
                    List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors =
                            config.getComponent(HandlerInterceptorRegistry.class).commandInterceptors(config);
                    List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors =
                            config.getComponent(DispatchInterceptorRegistry.class).commandInterceptors(config);
                    return handlerInterceptors.isEmpty() && dispatchInterceptors.isEmpty()
                            ? delegate
                            : new InterceptingCommandBus(delegate, handlerInterceptors, dispatchInterceptors);
                }
        );
        registry.registerDecorator(
                EventSink.class,
                InterceptingEventSink.DECORATION_ORDER,
                (config, name, delegate) -> {
                    if (!isDirectImplementationOf(delegate, EventSink.class)) {
                        return delegate;
                    }
                    List<MessageDispatchInterceptor<? super EventMessage>> dispatchInterceptors =
                            config.getComponent(DispatchInterceptorRegistry.class).eventInterceptors(config);
                    return dispatchInterceptors.isEmpty()
                            ? delegate
                            : new InterceptingEventSink(delegate, dispatchInterceptors);
                }
        );
    }

    private static boolean isDirectImplementationOf(@Nonnull Object component,
                                                    @Nonnull Class<EventSink> clazz) {
        return Arrays.stream(component.getClass().getInterfaces())
                     .anyMatch(iface -> iface == clazz);
    }
}