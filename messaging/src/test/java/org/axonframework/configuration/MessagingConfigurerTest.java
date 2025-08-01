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
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.NamespaceMessageTypeResolver;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
        assertInstanceOf(DefaultCommandGateway.class, commandGateway.get());

        Optional<CommandBus> commandBus = result.getOptionalComponent(CommandBus.class);
        assertTrue(commandBus.isPresent());
        assertInstanceOf(SimpleCommandBus.class, commandBus.get());

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

        Optional<QueryUpdateEmitter> queryUpdateEmitter = result.getOptionalComponent(QueryUpdateEmitter.class);
        assertTrue(queryUpdateEmitter.isPresent());
        assertInstanceOf(SimpleQueryUpdateEmitter.class, queryUpdateEmitter.get());
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
        CommandBus expected = new SimpleCommandBus();

        Configuration result = testSubject.registerCommandBus(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(CommandBus.class));
    }

    @Test
    void registerEventSinkOverridesDefault() {
        EventSink expected = (context, events) -> FutureUtils.emptyCompletedFuture();

        Configuration result = testSubject.registerEventSink(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(EventSink.class));
    }

    @Test
    void registerQueryBusOverridesDefault() {
        QueryBus expected = SimpleQueryBus.builder().build();

        Configuration result = testSubject.registerQueryBus(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(QueryBus.class));
    }

    @Test
    void registerQueryUpdateEmitterOverridesDefault() {
        QueryUpdateEmitter expected = SimpleQueryUpdateEmitter.builder().build();

        Configuration result = testSubject.registerQueryUpdateEmitter(c -> expected)
                                          .build();

        assertEquals(expected, result.getComponent(QueryUpdateEmitter.class));
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