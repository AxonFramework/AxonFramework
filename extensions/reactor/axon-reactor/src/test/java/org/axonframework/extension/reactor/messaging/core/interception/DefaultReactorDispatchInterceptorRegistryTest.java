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

package org.axonframework.extension.reactor.messaging.core.interception;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test class validating the {@link DefaultReactorDispatchInterceptorRegistry}.
 */
class DefaultReactorDispatchInterceptorRegistryTest {

    private DefaultReactorDispatchInterceptorRegistry testSubject;
    private Configuration mockConfig;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultReactorDispatchInterceptorRegistry();
        mockConfig = mock(Configuration.class);
    }

    @Test
    void registeredGenericInterceptorIsReturnedForAllTypes() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerInterceptor(c -> new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredGenericInterceptorsAreOnlyConstructedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        ReactorDispatchInterceptorRegistry result = testSubject.registerInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new GenericReactorMessageDispatchInterceptor();
        });

        result.commandInterceptors(mockConfig, Object.class, null);
        result.commandInterceptors(mockConfig, Object.class, null);
        result.eventInterceptors(mockConfig, Object.class, null);
        result.eventInterceptors(mockConfig, Object.class, null);
        result.queryInterceptors(mockConfig, Object.class, null);
        result.queryInterceptors(mockConfig, Object.class, null);

        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredGenericInterceptorFactoryIsReturnedForAllTypes() {
        ReactorDispatchInterceptorRegistry result = testSubject.registerInterceptor(
                (c, type, name) -> new GenericReactorMessageDispatchInterceptor()
        );

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void returningNullFromInterceptorFactoryRunsSafely() {
        ReactorDispatchInterceptorRegistry result = testSubject.registerInterceptor((c, type, name) -> null);

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorIsReturnedFromCommandInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(c -> new CommandReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredCommandInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        ReactorDispatchInterceptorRegistry result = testSubject.registerCommandInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new CommandReactorDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        commandInterceptors = result.commandInterceptors(mockConfig, Object.class, null);
        commandInterceptors = result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredCommandInterceptorFactoryMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = Object.class;
        String expectedName = "commandGatewayName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        ReactorDispatchInterceptorRegistry result = testSubject.registerCommandInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new CommandReactorDispatchInterceptor();
                }
        );

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, expectedType, expectedName);
        assertThat(commandInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void canRegisterGenericInterceptorForCommandsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(c -> new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorIsReturnedFromEventInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerEventInterceptor(c -> new EventReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        ReactorDispatchInterceptorRegistry result = testSubject.registerEventInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new EventReactorDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        eventInterceptors = result.eventInterceptors(mockConfig, Object.class, null);
        eventInterceptors = result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredEventInterceptorFactoryMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = Object.class;
        String expectedName = "eventGatewayName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        ReactorDispatchInterceptorRegistry result = testSubject.registerEventInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new EventReactorDispatchInterceptor();
                }
        );

        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, expectedType, expectedName);
        assertThat(eventInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void canRegisterGenericInterceptorForEventsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerEventInterceptor(c -> new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryInterceptorIsReturnedFromQueryInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerQueryInterceptor(c -> new QueryReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredQueryInterceptorsAreOnlyCreatedOnce() {
        AtomicInteger builderInvocationCount = new AtomicInteger(0);
        ReactorDispatchInterceptorRegistry result = testSubject.registerQueryInterceptor(c -> {
            builderInvocationCount.incrementAndGet();
            return new QueryReactorDispatchInterceptor();
        });

        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        //noinspection UnusedAssignment | Additional invocations are on purpose to validate the builder is invoked once.
        queryInterceptors = result.queryInterceptors(mockConfig, Object.class, null);
        queryInterceptors = result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
        assertThat(builderInvocationCount.get()).isEqualTo(1);
    }

    @Test
    void registeredQueryInterceptorFactoryMayAdjustConstructionForGivenTypeAndName() {
        Class<?> expectedType = Object.class;
        String expectedName = "queryGatewayName";

        AtomicReference<Class<?>> givenType = new AtomicReference<>();
        AtomicReference<String> givenName = new AtomicReference<>();
        ReactorDispatchInterceptorRegistry result = testSubject.registerQueryInterceptor(
                (c, type, name) -> {
                    givenType.set(type);
                    givenName.set(name);
                    return new QueryReactorDispatchInterceptor();
                }
        );

        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, expectedType, expectedName);
        assertThat(queryInterceptors).size().isEqualTo(1);
        assertThat(givenType).hasValue(expectedType);
        assertThat(givenName).hasValue(expectedName);
    }

    @Test
    void canRegisterGenericInterceptorForQueriesOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerQueryInterceptor(c -> new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors =
                result.commandInterceptors(mockConfig, Object.class, null);
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors =
                result.eventInterceptors(mockConfig, Object.class, null);
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors =
                result.queryInterceptors(mockConfig, Object.class, null);
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    static class GenericReactorMessageDispatchInterceptor implements ReactorMessageDispatchInterceptor<Message> {

        @Override
        public Mono<?> interceptOnDispatch(Message message,
                                           @Nullable ProcessingContext context,
                                           ReactorMessageDispatchInterceptorChain<Message> chain) {
            return chain.proceed(message, context);
        }
    }

    static class CommandReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<CommandMessage> {

        @Override
        public Mono<?> interceptOnDispatch(CommandMessage message,
                                           @Nullable ProcessingContext context,
                                           ReactorMessageDispatchInterceptorChain<CommandMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class EventReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<EventMessage> {

        @Override
        public Mono<?> interceptOnDispatch(EventMessage message,
                                           @Nullable ProcessingContext context,
                                           ReactorMessageDispatchInterceptorChain<EventMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class QueryReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<QueryMessage> {

        @Override
        public Mono<?> interceptOnDispatch(QueryMessage message,
                                           @Nullable ProcessingContext context,
                                           ReactorMessageDispatchInterceptorChain<QueryMessage> chain) {
            return chain.proceed(message, context);
        }
    }
}
