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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultInterceptorRegistry}.
 *
 * @author Steven van Beelen
 */
class DefaultInterceptorRegistryTest {

    private InterceptorRegistry testSubject;

    private Configuration config;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultInterceptorRegistry();

        config = mock(Configuration.class);
    }

    @Test
    void registeredGenericHandlerInterceptorsIsReturnedForAllTypes() {
        InterceptorRegistry result = testSubject.registerHandlerInterceptor(c -> new GenericMessageHandlerInterceptor());

        List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors = result.commandHandlerInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<EventMessage>> eventInterceptors = result.eventHandlerInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors = result.queryHandlerInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredCommandHandlerInterceptorsIsReturnedFromCommandHandlerInterceptorsOnly() {
        InterceptorRegistry result = testSubject.registerCommandHandlerInterceptor(c -> new CommandHandlerInterceptor());

        List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors = result.commandHandlerInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<EventMessage>> eventInterceptors = result.eventHandlerInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors = result.queryHandlerInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventHandlerInterceptorsIsReturnedFromEventHandlerInterceptorsOnly() {
        InterceptorRegistry result = testSubject.registerEventHandlerInterceptor(c -> new EventHandlerInterceptor());

        List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors = result.commandHandlerInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<EventMessage>> eventInterceptors = result.eventHandlerInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors = result.queryHandlerInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryHandlerInterceptorsIsReturnedFromQueryHandlerInterceptorsOnly() {
        InterceptorRegistry result = testSubject.registerQueryHandlerInterceptor(c -> new QueryHandlerInterceptor());

        List<MessageHandlerInterceptor<CommandMessage>> commandInterceptors = result.commandHandlerInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<EventMessage>> eventInterceptors = result.eventHandlerInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<QueryMessage>> queryInterceptors = result.queryHandlerInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    static class GenericMessageHandlerInterceptor implements MessageHandlerInterceptor<Message> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull Message message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<Message> chain) {
            return chain.proceed(message, context);
        }
    }

    static class CommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull CommandMessage message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<CommandMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class EventHandlerInterceptor implements MessageHandlerInterceptor<EventMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull EventMessage message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<EventMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class QueryHandlerInterceptor implements MessageHandlerInterceptor<QueryMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnHandle(@Nonnull QueryMessage message,
                                                  @Nonnull ProcessingContext context,
                                                  @Nonnull MessageHandlerInterceptorChain<QueryMessage> chain) {
            return chain.proceed(message, context);
        }
    }
}