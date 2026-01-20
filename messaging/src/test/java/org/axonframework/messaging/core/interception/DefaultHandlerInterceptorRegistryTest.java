/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultHandlerInterceptorRegistry}.
 *
 * @author Steven van Beelen
 */
class DefaultHandlerInterceptorRegistryTest {

    private HandlerInterceptorRegistry testSubject;

    private Configuration config;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultHandlerInterceptorRegistry();

        config = mock(Configuration.class);
    }

    @Test
    void registeredGenericInterceptorsIsReturnedForAllTypes() {
        HandlerInterceptorRegistry result = testSubject.registerInterceptor(c -> new GenericMessageHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredGenericInterceptorsAreOnlyConstructedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        HandlerInterceptorRegistry result = testSubject.registerInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new GenericMessageHandlerInterceptor();
        });

        result.commandInterceptors(config);
        result.commandInterceptors(config);
        result.eventInterceptors(config);
        result.eventInterceptors(config);
        result.queryInterceptors(config);
        result.queryInterceptors(config);

        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredCommandInterceptorsIsReturnedFromCommandInterceptorsOnly() {
        HandlerInterceptorRegistry result = testSubject.registerCommandInterceptor(c -> new CommandHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        HandlerInterceptorRegistry result = testSubject.registerCommandInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new CommandHandlerInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        commandInterceptors = result.commandInterceptors(config);
        commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForCommandsOnly() {
        HandlerInterceptorRegistry result =
                testSubject.registerCommandInterceptor(c -> new GenericMessageHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorsIsReturnedFromEventInterceptorsOnly() {
        HandlerInterceptorRegistry result = testSubject.registerEventInterceptor(c -> new EventHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        HandlerInterceptorRegistry result = testSubject.registerEventInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new EventHandlerInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        eventInterceptors = result.eventInterceptors(config);
        eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForEventsOnly() {
        HandlerInterceptorRegistry result =
                testSubject.registerEventInterceptor(c -> new GenericMessageHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryInterceptorsIsReturnedFromQueryInterceptorsOnly() {
        HandlerInterceptorRegistry result = testSubject.registerQueryInterceptor(c -> new QueryHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredQueryInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        HandlerInterceptorRegistry result = testSubject.registerQueryInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new QueryHandlerInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        queryInterceptors = result.queryInterceptors(config);
        queryInterceptors = result.queryInterceptors(config);
        assertThat(queryInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForQueriesOnly() {
        HandlerInterceptorRegistry result =
                testSubject.registerQueryInterceptor(c -> new GenericMessageHandlerInterceptor());

        List<MessageHandlerInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors(config);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors(config);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageHandlerInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors(config);
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