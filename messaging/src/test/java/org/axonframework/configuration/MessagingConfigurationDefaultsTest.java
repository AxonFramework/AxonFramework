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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandPriorityCalculator;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.RoutingStrategy;
import org.axonframework.commandhandling.annotations.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.ConvertingCommandGateway;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.InterceptingEventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.DefaultDispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.DefaultHandlerInterceptorRegistry;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.HandlerInterceptorRegistry;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryPriorityCalculator;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MessagingConfigurationDefaults}.
 *
 * @author Steven van Beelen
 */
class MessagingConfigurationDefaultsTest {

    private MessagingConfigurationDefaults testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new MessagingConfigurationDefaults();
    }

    @Test
    void orderEqualsMaxInteger() {
        assertEquals(Integer.MAX_VALUE, testSubject.order());
    }

    @Test
    void enhanceSetsExpectedDefaultsInAbsenceOfTheseComponents() {
        ApplicationConfigurer configurer = new DefaultAxonApplication();
        configurer.componentRegistry(cr -> testSubject.enhance(cr));
        Configuration resultConfig = configurer.build();

        assertInstanceOf(ClassBasedMessageTypeResolver.class, resultConfig.getComponent(MessageTypeResolver.class));
        Converter generalConverter = resultConfig.getComponent(Converter.class);
        assertInstanceOf(JacksonConverter.class, generalConverter);
        MessageConverter messageConverter = resultConfig.getComponent(MessageConverter.class);
        assertInstanceOf(DelegatingMessageConverter.class, messageConverter);
        EventConverter eventConverter = resultConfig.getComponent(EventConverter.class);
        assertInstanceOf(DelegatingEventConverter.class, eventConverter);
        Converter messageConverterDelegate = ((DelegatingMessageConverter) messageConverter).delegate();
        assertThat(generalConverter).isEqualTo(messageConverterDelegate);
        MessageConverter eventConverterDelegate = ((DelegatingEventConverter) eventConverter).delegate();
        assertThat(messageConverter).isEqualTo(eventConverterDelegate);
        assertThat(resultConfig.getComponent(CorrelationDataProviderRegistry.class))
                .isInstanceOf(DefaultCorrelationDataProviderRegistry.class);
        assertThat(resultConfig.getComponent(DispatchInterceptorRegistry.class))
                .isInstanceOf(DefaultDispatchInterceptorRegistry.class);
        assertThat(resultConfig.getComponent(HandlerInterceptorRegistry.class))
                .isInstanceOf(DefaultHandlerInterceptorRegistry.class);
        assertInstanceOf(TransactionalUnitOfWorkFactory.class, resultConfig.getComponent(UnitOfWorkFactory.class));
        // Intercepting at all times, since we have a MessageOriginProvider that leads to the CorrelationDataInterceptor
        assertInstanceOf(InterceptingCommandBus.class, resultConfig.getComponent(CommandBus.class));
        assertEquals(CommandPriorityCalculator.defaultCalculator(),
                     resultConfig.getComponent(CommandPriorityCalculator.class));
        assertInstanceOf(AnnotationRoutingStrategy.class, resultConfig.getComponent(RoutingStrategy.class));
        // The specific CommandGateway-implementation registered by default may be overridden by the serviceloader-mechanism.
        // So we just check if _any_ CommandGateway has been added to the configuration.
        assertTrue(resultConfig.hasComponent(CommandGateway.class));
        assertInstanceOf(DefaultEventGateway.class, resultConfig.getComponent(EventGateway.class));
        assertInstanceOf(SimpleEventBus.class, resultConfig.getComponent(EventBus.class));
        assertInstanceOf(SimpleQueryBus.class, resultConfig.getComponent(QueryBus.class));
        assertEquals(QueryPriorityCalculator.defaultCalculator(),
                     resultConfig.getComponent(QueryPriorityCalculator.class));
        assertInstanceOf(DefaultQueryGateway.class, resultConfig.getComponent(QueryGateway.class));
    }

    @Test
    void registersMessageOriginProviderInCorrelationDataProviderRegistryByDefault() {
        ApplicationConfigurer configurer = new DefaultAxonApplication();
        configurer.componentRegistry(cr -> testSubject.enhance(cr));
        Configuration resultConfig = configurer.build();

        List<CorrelationDataProvider> providers = resultConfig.getComponent(CorrelationDataProviderRegistry.class)
                                                              .correlationDataProviders(resultConfig);

        assertThat(providers).size().isEqualTo(1);
        assertThat(providers.getFirst()).isInstanceOf(MessageOriginProvider.class);
    }

    @Test
    void registersCorrelationDataInterceptorInInterceptorRegistriesWhenSingleCorrelationDataProviderIsPresent() {
        ApplicationConfigurer configurer = new DefaultAxonApplication();
        configurer.componentRegistry(cr -> {
            cr.registerComponent(CorrelationDataProviderRegistry.class,
                                 config -> {
                                     DefaultCorrelationDataProviderRegistry providerRegistry =
                                             new DefaultCorrelationDataProviderRegistry();
                                     return providerRegistry.registerProvider(c -> mock());
                                 });
            testSubject.enhance(cr);
        });
        Configuration resultConfig = configurer.build();

        // Generic interceptors are wrapped for type safety and as such we cannot validate if the single interceptor is a CorrelationDataInterceptor
        DispatchInterceptorRegistry dispatchInterceptorRegistry =
                resultConfig.getComponent(DispatchInterceptorRegistry.class);
        assertThat(dispatchInterceptorRegistry.commandInterceptors(resultConfig)).size().isEqualTo(1);
        assertThat(dispatchInterceptorRegistry.eventInterceptors(resultConfig)).size().isEqualTo(1);
        assertThat(dispatchInterceptorRegistry.queryInterceptors(resultConfig)).size().isEqualTo(1);
        HandlerInterceptorRegistry handlerInterceptorRegistry =
                resultConfig.getComponent(HandlerInterceptorRegistry.class);
        assertThat(handlerInterceptorRegistry.commandInterceptors(resultConfig)).size().isEqualTo(1);
        assertThat(handlerInterceptorRegistry.eventInterceptors(resultConfig)).size().isEqualTo(1);
        assertThat(handlerInterceptorRegistry.queryInterceptors(resultConfig)).size().isEqualTo(1);
    }

    @Test
    void doesNotRegisterCorrelationDataInterceptorInInterceptorRegistriesWhenTheAreNoCorrelationDataProviders() {
        ApplicationConfigurer configurer = new DefaultAxonApplication();
        configurer.componentRegistry(cr -> {
            cr.registerComponent(CorrelationDataProviderRegistry.class,
                                 config -> new DefaultCorrelationDataProviderRegistry());
            testSubject.enhance(cr);
        });
        Configuration resultConfig = configurer.build();

        DispatchInterceptorRegistry dispatchInterceptorRegistry =
                resultConfig.getComponent(DispatchInterceptorRegistry.class);
        assertThat(dispatchInterceptorRegistry.commandInterceptors(resultConfig)).size().isEqualTo(0);
        assertThat(dispatchInterceptorRegistry.eventInterceptors(resultConfig)).size().isEqualTo(0);
        assertThat(dispatchInterceptorRegistry.queryInterceptors(resultConfig)).size().isEqualTo(0);
        HandlerInterceptorRegistry handlerInterceptorRegistry =
                resultConfig.getComponent(HandlerInterceptorRegistry.class);
        assertThat(handlerInterceptorRegistry.commandInterceptors(resultConfig)).size().isEqualTo(0);
        assertThat(handlerInterceptorRegistry.eventInterceptors(resultConfig)).size().isEqualTo(0);
        assertThat(handlerInterceptorRegistry.queryInterceptors(resultConfig)).size().isEqualTo(0);
    }

    @Test
    void enhanceOnlySetsDefaultsThatAreNotPresentYet() {
        TestCommandBus testCommandBus = new TestCommandBus();

        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        ApplicationConfigurer configurer = new DefaultAxonApplication().componentRegistry(
                cr -> cr.registerComponent(CommandBus.class, c -> testCommandBus)
                        .registerComponent(CorrelationDataProviderRegistry.class,
                                           c -> new DefaultCorrelationDataProviderRegistry())
                        .registerEnhancer(testSubject)
        );

        CommandBus configuredCommandBus = configurer.build()
                                                    .getComponent(CommandBus.class);

        assertEquals(testCommandBus, configuredCommandBus);
    }

    @Test
    void enhancesComponentRegistryWithConvertingCommandGateway() {
        MessagingConfigurer configurer = MessagingConfigurer.create();
        Configuration resultConfig = configurer.build();
        assertInstanceOf(ConvertingCommandGateway.class, resultConfig.getComponent(CommandGateway.class));
    }

    @Test
    void decoratorsCommandBusAsInterceptorCommandBusWhenGenericHandlerInterceptorIsPresent() {
        //noinspection unchecked
        MessagingConfigurer configurer =
                MessagingConfigurer.create()
                                   .registerMessageHandlerInterceptor(c -> mock(MessageHandlerInterceptor.class));

        Configuration resultConfig = configurer.build();

        assertThat(resultConfig.getComponent(CommandBus.class)).isInstanceOf(InterceptingCommandBus.class);
    }

    @Test
    void decoratorsCommandBusAsInterceptorCommandBusWhenCommandHandlerInterceptorIsPresent() {
        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        //noinspection unchecked
        MessagingConfigurer configurer =
                MessagingConfigurer.create()
                                   .componentRegistry(cr -> cr.registerComponent(
                                           CorrelationDataProviderRegistry.class,
                                           c -> new DefaultCorrelationDataProviderRegistry()
                                   ))
                                   .registerCommandHandlerInterceptor(c -> mock(MessageHandlerInterceptor.class));

        Configuration resultConfig = configurer.build();

        assertThat(resultConfig.getComponent(CommandBus.class)).isInstanceOf(InterceptingCommandBus.class);
    }

    @Test
    void decoratorsCommandBusAsInterceptorCommandBusWhenDispatchInterceptorIsPresent() {
        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor.
        //noinspection unchecked
        MessagingConfigurer configurer =
                MessagingConfigurer.create()
                                   .componentRegistry(cr -> cr.registerComponent(
                                           CorrelationDataProviderRegistry.class,
                                           c -> new DefaultCorrelationDataProviderRegistry()
                                   ))
                                   .registerDispatchInterceptor(c -> mock(MessageDispatchInterceptor.class));

        Configuration resultConfig = configurer.build();

        assertThat(resultConfig.getComponent(CommandBus.class)).isInstanceOf(InterceptingCommandBus.class);
    }

    @Test
    void decoratorsEventSinkAsInterceptorEventSinkWhenDispatchInterceptorIsPresent() {
        //noinspection unchecked
        MessagingConfigurer configurer =
                MessagingConfigurer.create()
                                   .componentRegistry(cr -> cr.registerComponent(
                                           CorrelationDataProviderRegistry.class,
                                           c -> new DefaultCorrelationDataProviderRegistry()
                                   ))
                                   .registerDispatchInterceptor(c -> mock(MessageDispatchInterceptor.class));

        Configuration resultConfig = configurer.build();

        assertThat(resultConfig.getComponent(EventSink.class)).isInstanceOf(InterceptingEventSink.class);
    }

    private static class TestCommandBus implements CommandBus {

        @Override
        public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                                @Nullable ProcessingContext processingContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }
    }
}