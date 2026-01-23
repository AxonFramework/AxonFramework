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
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultDispatchInterceptorRegistry}.
 *
 * @author Steven van Beelen
 */
class DefaultDispatchInterceptorRegistryTest {

    private DefaultDispatchInterceptorRegistry testSubject;

    private Configuration config;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultDispatchInterceptorRegistry();

        config = mock(Configuration.class);
    }

    @Test
    void registeredGenericInterceptorsIsReturnedForAllTypes() {
        DispatchInterceptorRegistry result = testSubject.registerInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredGenericInterceptorsAreOnlyConstructedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new GenericMessageDispatchInterceptor();
        });

        result.commandInterceptors(config, CommandBus.class, null);
        result.commandInterceptors(config, CommandBus.class, null);
        result.eventInterceptors(config, EventSink.class, null);
        result.eventInterceptors(config, EventSink.class, null);
        result.queryInterceptors(config, QueryBus.class, null);
        result.queryInterceptors(config, QueryBus.class, null);
        result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);

        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredGenericInterceptorsBuilderIsReturnedForAllTypes() {
        DispatchInterceptorRegistry result = testSubject.registerInterceptor(
                (c, type, name) -> new GenericMessageDispatchInterceptor()
        );

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(1);
    }

    @Test
    void returningNullFromInterceptorsBuilderRunsSafely() {
        DispatchInterceptorRegistry result = testSubject.registerInterceptor((c, type, name) -> null);

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorsIsReturnedFromCommandInterceptorsOnly() {
        DispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(c -> new CommandDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerCommandInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new CommandDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        commandInterceptors = result.commandInterceptors(config, CommandBus.class, null);
        commandInterceptors = result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForCommandsOnly() {
        DispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorsBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = CommandBus.class;
        String expectedName = "commandBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        DispatchInterceptorRegistry result = testSubject.registerCommandInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new CommandDispatchInterceptor();
                }
        );

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, expectedType, expectedName);
        assertThat(commandInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeredEventInterceptorsIsReturnedFromEventInterceptorsOnly() {
        DispatchInterceptorRegistry result = testSubject.registerEventInterceptor(c -> new EventDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerEventInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new EventDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        eventInterceptors = result.eventInterceptors(config, EventSink.class, null);
        eventInterceptors = result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForEventsOnly() {
        DispatchInterceptorRegistry result =
                testSubject.registerEventInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorsBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = EventSink.class;
        String expectedName = "eventSinkName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        DispatchInterceptorRegistry result = testSubject.registerEventInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new EventDispatchInterceptor();
                }
        );

        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, expectedType, expectedName);
        assertThat(eventInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeredQueryInterceptorsIsReturnedFromQueryInterceptorsOnly() {
        DispatchInterceptorRegistry result = testSubject.registerQueryInterceptor(c -> new QueryDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerQueryInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new QueryDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        queryInterceptors = result.queryInterceptors(config, QueryBus.class, null);
        queryInterceptors = result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForQueriesOnly() {
        DispatchInterceptorRegistry result =
                testSubject.registerQueryInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryInterceptorsBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = QueryBus.class;
        String expectedName = "queryBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        DispatchInterceptorRegistry result = testSubject.registerQueryInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new QueryDispatchInterceptor();
                }
        );

        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, expectedType, expectedName);
        assertThat(queryInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void registeredSubscriptionQueryUpdateInterceptorsIsReturnedFromSubscriptionQueryUpdateInterceptorsOnly() {
        DispatchInterceptorRegistry result = testSubject.registerSubscriptionQueryUpdateInterceptor(c -> new SubscriptionQueryUpdateDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredSubscriptionQueryUpdateInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        DispatchInterceptorRegistry result = testSubject.registerSubscriptionQueryUpdateInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new SubscriptionQueryUpdateDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        subscriptionQueryUpdateInterceptors = result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        subscriptionQueryUpdateInterceptors = result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForSubscriptionQueryUpdatesOnly() {
        DispatchInterceptorRegistry result =
                testSubject.registerSubscriptionQueryUpdateInterceptor(c -> new GenericMessageDispatchInterceptor());

        List<MessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(config, CommandBus.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(config, EventSink.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(config, QueryBus.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryUpdateInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, QueryBus.class, null);
        assertThat(subscriptionQueryUpdateInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredSubscriptionQueryInterceptorsBuilderMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = QueryBus.class;
        String expectedName = "queryBusName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        DispatchInterceptorRegistry result = testSubject.registerSubscriptionQueryUpdateInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new SubscriptionQueryUpdateDispatchInterceptor();
                }
        );

        List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage>> subscriptionQueryInterceptors =
                result.subscriptionQueryUpdateInterceptors(config, expectedType, expectedName);
        assertThat(subscriptionQueryInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    static class GenericMessageDispatchInterceptor implements MessageDispatchInterceptor<Message> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull Message message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<Message> chain) {
            return chain.proceed(message, context);
        }
    }

    static class CommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull CommandMessage message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<CommandMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class EventDispatchInterceptor implements MessageDispatchInterceptor<EventMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull EventMessage message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<EventMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class QueryDispatchInterceptor implements MessageDispatchInterceptor<QueryMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull QueryMessage message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<QueryMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class SubscriptionQueryUpdateDispatchInterceptor
            implements MessageDispatchInterceptor<SubscriptionQueryUpdateMessage> {

        @Nonnull
        @Override
        public MessageStream<?> interceptOnDispatch(@Nonnull SubscriptionQueryUpdateMessage message,
                                                    @Nullable ProcessingContext context,
                                                    @Nonnull MessageDispatchInterceptorChain<SubscriptionQueryUpdateMessage> chain) {
            return chain.proceed(message, context);
        }
    }
}