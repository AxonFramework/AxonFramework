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
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.ConvertingCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.NamespaceMessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.messaging.interceptors.DispatchInterceptorRegistry;
import org.axonframework.messaging.interceptors.HandlerInterceptorRegistry;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusTestUtils;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.configuration.QueryHandlingModule;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.commandhandling.CommandBusTestUtils.aCommandBus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MessagingConfigurer}.
 *
 * @author Steven van Beelen
 */
class MessagingConfigurerTest extends ApplicationConfigurerTestSuite<MessagingConfigurer> {

    @Override
    public MessagingConfigurer createConfigurer() {
        return testSubject == null ? MessagingConfigurer.create() : testSubject;
    }

    @Test
    void defaultComponents() {
        Configuration result = testSubject.build();

        Optional<MessageTypeResolver> messageTypeResolver = result.getOptionalComponent(MessageTypeResolver.class);
        assertTrue(messageTypeResolver.isPresent());
        assertInstanceOf(ClassBasedMessageTypeResolver.class, messageTypeResolver.get());

        Optional<CommandGateway> commandGateway = result.getOptionalComponent(CommandGateway.class);
        assertTrue(commandGateway.isPresent());
        assertInstanceOf(ConvertingCommandGateway.class, commandGateway.get());

        Optional<CommandBus> commandBus = result.getOptionalComponent(CommandBus.class);
        assertTrue(commandBus.isPresent());
        // Intercepting at all times, since we have a MessageOriginProvider that leads to the CorrelationDataInterceptor
        assertInstanceOf(InterceptingCommandBus.class, commandBus.get());

        Optional<EventGateway> eventGateway = result.getOptionalComponent(EventGateway.class);
        assertTrue(eventGateway.isPresent());
        assertInstanceOf(DefaultEventGateway.class, eventGateway.get());

        Optional<EventSink> eventSink = result.getOptionalComponent(EventSink.class);
        assertTrue(eventSink.isPresent());

        Optional<EventBus> eventBus = result.getOptionalComponent(EventBus.class);
        assertTrue(eventBus.isPresent());
        assertInstanceOf(SimpleEventBus.class, eventBus.get());

        Optional<QueryGateway> queryGateway = result.getOptionalComponent(QueryGateway.class);
        assertTrue(queryGateway.isPresent());
        assertInstanceOf(DefaultQueryGateway.class, queryGateway.get());

        Optional<QueryBus> queryBus = result.getOptionalComponent(QueryBus.class);
        assertTrue(queryBus.isPresent());
        assertInstanceOf(SimpleQueryBus.class, queryBus.get());
    }

    @Test
    void registerMessageTypeResolverOverridesDefault() {
        MessageTypeResolver expected = NamespaceMessageTypeResolver
                .namespace("namespace")
                .message(String.class, "test.message", "1.0.0")
                .noFallback();

        Configuration result = testSubject.registerMessageTypeResolver(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(MessageTypeResolver.class));
    }

    @Test
    void messageTypeResolverBuilderShouldBeMutable() {
        NamespaceMessageTypeResolver.Builder builder1 = NamespaceMessageTypeResolver
                .namespace("namespace")
                .message(String.class, "test.string", "1.0.0");
        NamespaceMessageTypeResolver.Builder builder2 = builder1
                .message(Integer.class, "test.integer", "1.0.0");

        var instance1 = builder1.noFallback();
        var instance2 = builder2.fallback(new ClassBasedMessageTypeResolver());

        assertTrue(instance1.resolve(String.class).isPresent());
        assertTrue(instance1.resolve(Integer.class).isPresent());
        assertTrue(instance2.resolve(String.class).isPresent());
        assertTrue(instance2.resolve(Integer.class).isPresent());
    }

    @Test
    void registerCommandBusOverridesDefault() {
        CommandBus expected = aCommandBus();

        // Overriding CorrelationDataProviderRegistry ensures CorrelationDataInterceptor is not build.
        // This otherwise leads to the InterceptingCommandBus
        Configuration result = testSubject.componentRegistry(cr -> cr.registerComponent(
                                                  CorrelationDataProviderRegistry.class,
                                                  c -> new DefaultCorrelationDataProviderRegistry()
                                          ))
                                          .registerCommandBus(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(CommandBus.class));
    }

    @Test
    void registerEventSinkOverridesDefault() {
        EventSink expected = new EventSink() {
            @Override
            public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                                   @Nonnull List<EventMessage> events) {
                return FutureUtils.emptyCompletedFuture();
            }

            @Override
            public void describeTo(@Nonnull ComponentDescriptor descriptor) {
                throw new UnsupportedOperationException("Unimportant for this test case");
            }
        };

        // Overriding CorrelationDataProviderRegistry ensures CorrelationDataInterceptor is not build.
        // This otherwise leads to the InterceptingCommandBus
        Configuration result = testSubject.componentRegistry(cr -> cr.registerComponent(
                                                  CorrelationDataProviderRegistry.class,
                                                  c -> new DefaultCorrelationDataProviderRegistry()
                                          )).registerEventSink(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(EventSink.class));
    }

    @Test
    void registerQueryBusOverridesDefault() {
        QueryBus expected = QueryBusTestUtils.aQueryBus();

        Configuration result = testSubject.registerQueryBus(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(QueryBus.class));
    }

    @Test
    void registerCorrelationDataProviderMakesProviderRetrievableThroughProviderRegistry() {
        CorrelationDataProvider provider = mock(CorrelationDataProvider.class);

        Configuration result = testSubject.registerCorrelationDataProvider(c -> provider)
                                          .build();

        List<CorrelationDataProvider> interceptors =
                result.getComponent(CorrelationDataProviderRegistry.class)
                      .correlationDataProviders(result);
        assertThat(interceptors).contains(provider);
    }

    @Test
    void registerDispatchInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistryForAllTypes() {
        AtomicInteger counter = new AtomicInteger();
        MessageDispatchInterceptor<Message> dispatchInterceptor = (message, context, interceptorChain) -> {
            counter.incrementAndGet();
            //noinspection DataFlowIssue | Result is not important to validate invocation
            return null;
        };

        // Overriding CorrelationDataProviderRegistry ensures CorrelationDataInterceptor is not present.
        Configuration result = testSubject.componentRegistry(cr -> cr.registerComponent(
                                                  CorrelationDataProviderRegistry.class,
                                                  c -> new DefaultCorrelationDataProviderRegistry()
                                          ))
                                          .registerDispatchInterceptor(c -> dispatchInterceptor)
                                          .build();
        DispatchInterceptorRegistry interceptorRegistry = result.getComponent(DispatchInterceptorRegistry.class);

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                interceptorRegistry.commandInterceptors(result);
        assertThat(commandInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        commandInterceptors.getFirst().interceptOnDispatch(null, null, null);
        assertThat(counter).hasValue(1);

        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                interceptorRegistry.eventInterceptors(result);
        assertThat(eventInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        eventInterceptors.getFirst().interceptOnDispatch(null, null, null);
        assertThat(counter).hasValue(2);

        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                interceptorRegistry.queryInterceptors(result);
        assertThat(queryInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        queryInterceptors.getFirst().interceptOnDispatch(null, null, null);
        assertThat(counter).hasValue(3);
    }

    @Test
    void registerCommandDispatchInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageDispatchInterceptor<CommandMessage> handlerInterceptor = mock(MessageDispatchInterceptor.class);

        Configuration result = testSubject.registerCommandDispatchInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageDispatchInterceptor<? super CommandMessage>> interceptors =
                result.getComponent(DispatchInterceptorRegistry.class)
                      .commandInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerEventDispatchInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageDispatchInterceptor<EventMessage> handlerInterceptor = mock(MessageDispatchInterceptor.class);

        Configuration result = testSubject.registerEventDispatchInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageDispatchInterceptor<? super EventMessage>> interceptors =
                result.getComponent(DispatchInterceptorRegistry.class)
                      .eventInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerQueryDispatchInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageDispatchInterceptor<QueryMessage> handlerInterceptor = mock(MessageDispatchInterceptor.class);

        Configuration result = testSubject.registerQueryDispatchInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageDispatchInterceptor<? super QueryMessage>> interceptors =
                result.getComponent(DispatchInterceptorRegistry.class)
                      .queryInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerMessageHandlerInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistryForAllTypes() {
        AtomicInteger counter = new AtomicInteger();
        MessageHandlerInterceptor<Message> handlerInterceptor = (message, context, interceptorChain) -> {
            counter.incrementAndGet();
            //noinspection DataFlowIssue | Result is not important to validate invocation
            return null;
        };

        // Overriding CorrelationDataProviderRegistry ensures CorrelationDataInterceptor is not present.
        Configuration result = testSubject.componentRegistry(cr -> cr.registerComponent(
                                                  CorrelationDataProviderRegistry.class,
                                                  c -> new DefaultCorrelationDataProviderRegistry()
                                          ))
                                          .registerMessageHandlerInterceptor(c -> handlerInterceptor)
                                          .build();
        HandlerInterceptorRegistry handlerInterceptorRegistry = result.getComponent(HandlerInterceptorRegistry.class);

        List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors =
                handlerInterceptorRegistry.commandInterceptors(result);
        assertThat(commandInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        commandInterceptors.getFirst().interceptOnHandle(null, null, null);
        assertThat(counter).hasValue(1);

        List<MessageHandlerInterceptor<EventMessage>> eventInterceptors =
                handlerInterceptorRegistry.eventInterceptors(result);
        assertThat(eventInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        eventInterceptors.getFirst().interceptOnHandle(null, null, null);
        assertThat(counter).hasValue(2);

        List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors =
                handlerInterceptorRegistry.queryInterceptors(result);
        assertThat(queryInterceptors).hasSize(1);
        //noinspection DataFlowIssue | Input is not important to validate invocation
        queryInterceptors.getFirst().interceptOnHandle(null, null, null);
        assertThat(counter).hasValue(3);
    }

    @Test
    void registerCommandHandlerInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageHandlerInterceptor<CommandMessage> handlerInterceptor = mock(MessageHandlerInterceptor.class);

        Configuration result = testSubject.registerCommandHandlerInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageHandlerInterceptor<CommandMessage>> interceptors = result.getComponent(HandlerInterceptorRegistry.class)
                                                                             .commandInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerEventHandlerInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageHandlerInterceptor<EventMessage> handlerInterceptor = mock(MessageHandlerInterceptor.class);

        Configuration result = testSubject.registerEventHandlerInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageHandlerInterceptor<EventMessage>> interceptors = result.getComponent(HandlerInterceptorRegistry.class)
                                                                           .eventInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerQueryHandlerInterceptorMakesInterceptorRetrievableThroughTheInterceptorRegistry() {
        //noinspection unchecked
        MessageHandlerInterceptor<QueryMessage> handlerInterceptor = mock(MessageHandlerInterceptor.class);

        Configuration result = testSubject.registerQueryHandlerInterceptor(c -> handlerInterceptor)
                                          .build();

        List<MessageHandlerInterceptor<QueryMessage>> interceptors = result.getComponent(HandlerInterceptorRegistry.class)
                                                                           .queryInterceptors(result);
        assertThat(interceptors).contains(handlerInterceptor);
    }

    @Test
    void registerCommandHandlingModuleAddsAModuleConfiguration() {
        ModuleBuilder<CommandHandlingModule> statelessCommandHandlingModule =
                CommandHandlingModule.named("test")
                                     .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                             new QualifiedName(String.class),
                                             (command, context) -> MessageStream.empty().cast()
                                     ));

        Configuration configuration =
                testSubject.registerCommandHandlingModule(statelessCommandHandlingModule)
                           .build();

        assertThat(configuration.getModuleConfiguration("test")).isPresent();
    }

    @Test
    void registerQueryHandlingModuleAddsAModuleConfiguration() {
        ModuleBuilder<QueryHandlingModule> statefulCommandHandlingModule =
                QueryHandlingModule.named("test")
                                   .queryHandlers(handlerPhase -> handlerPhase.queryHandler(
                                           new QualifiedName(String.class),
                                           new QualifiedName(String.class),
                                           (command, context) -> MessageStream.empty().cast()
                                   ));

        Configuration configuration =
                testSubject.registerQueryHandlingModule(statefulCommandHandlingModule)
                           .build();

        assertThat(configuration.getModuleConfiguration("test")).isPresent();
    }

    @Test
    void applicationDelegatesTasks() {
        TestComponent tc = new TestComponent();
        TestComponent result =
                testSubject.componentRegistry(axon -> axon.registerComponent(TestComponent.class, c -> tc))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(tc, result);
    }

    private static class TestComponent {

    }
}