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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptorChain;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link DefaultReactorDispatchInterceptorRegistry}.
 */
class DefaultReactorDispatchInterceptorRegistryTest {

    private DefaultReactorDispatchInterceptorRegistry testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultReactorDispatchInterceptorRegistry();
    }

    @Test
    void registeredGenericInterceptorIsReturnedForAllTypes() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerInterceptor(new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void registeredCommandInterceptorIsReturnedFromCommandInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(new CommandReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void canRegisterGenericInterceptorForCommandsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerCommandInterceptor(new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredEventInterceptorIsReturnedFromEventInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerEventInterceptor(new EventReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void canRegisterGenericInterceptorForEventsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerEventInterceptor(new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(1);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(0);
    }

    @Test
    void registeredQueryInterceptorIsReturnedFromQueryInterceptorsOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerQueryInterceptor(new QueryReactorDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    @Test
    void canRegisterGenericInterceptorForQueriesOnly() {
        ReactorDispatchInterceptorRegistry result =
                testSubject.registerQueryInterceptor(new GenericReactorMessageDispatchInterceptor());

        List<ReactorMessageDispatchInterceptor<? super CommandMessage>> commandInterceptors = result.commandInterceptors();
        assertThat(commandInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super EventMessage>> eventInterceptors = result.eventInterceptors();
        assertThat(eventInterceptors).size().isEqualTo(0);
        List<ReactorMessageDispatchInterceptor<? super QueryMessage>> queryInterceptors = result.queryInterceptors();
        assertThat(queryInterceptors).size().isEqualTo(1);
    }

    static class GenericReactorMessageDispatchInterceptor implements ReactorMessageDispatchInterceptor<Message> {

        @NonNull
        @Override
        public Mono<?> interceptOnDispatch(@NonNull Message message,
                                           @Nullable ProcessingContext context,
                                           @NonNull ReactorMessageDispatchInterceptorChain<Message> chain) {
            return chain.proceed(message, context);
        }
    }

    static class CommandReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<CommandMessage> {

        @NonNull
        @Override
        public Mono<?> interceptOnDispatch(@NonNull CommandMessage message,
                                           @Nullable ProcessingContext context,
                                           @NonNull ReactorMessageDispatchInterceptorChain<CommandMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class EventReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<EventMessage> {

        @NonNull
        @Override
        public Mono<?> interceptOnDispatch(@NonNull EventMessage message,
                                           @Nullable ProcessingContext context,
                                           @NonNull ReactorMessageDispatchInterceptorChain<EventMessage> chain) {
            return chain.proceed(message, context);
        }
    }

    static class QueryReactorDispatchInterceptor implements ReactorMessageDispatchInterceptor<QueryMessage> {

        @NonNull
        @Override
        public Mono<?> interceptOnDispatch(@NonNull QueryMessage message,
                                           @Nullable ProcessingContext context,
                                           @NonNull ReactorMessageDispatchInterceptorChain<QueryMessage> chain) {
            return chain.proceed(message, context);
        }
    }
}
