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
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

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
        ApplicationConfigurer<?> configurer = AxonApplication.create();
        testSubject.enhance(configurer);
        NewConfiguration resultConfig = configurer.build();

        assertInstanceOf(ClassBasedMessageTypeResolver.class, resultConfig.getComponent(MessageTypeResolver.class));
        assertInstanceOf(DefaultCommandGateway.class, resultConfig.getComponent(CommandGateway.class));
        assertInstanceOf(SimpleCommandBus.class, resultConfig.getComponent(CommandBus.class));
        assertInstanceOf(DefaultEventGateway.class, resultConfig.getComponent(EventGateway.class));
        assertInstanceOf(SimpleEventBus.class, resultConfig.getComponent(EventBus.class));
        assertInstanceOf(DefaultQueryGateway.class, resultConfig.getComponent(QueryGateway.class));
        assertInstanceOf(SimpleQueryBus.class, resultConfig.getComponent(QueryBus.class));
        assertInstanceOf(SimpleQueryUpdateEmitter.class, resultConfig.getComponent(QueryUpdateEmitter.class));
    }

    @Test
    void enhanceOnlySetsDefaultsThatAreNotPresentYet() {
        TestCommandBus testCommandBus = new TestCommandBus();

        ApplicationConfigurer<?> configurer =
                AxonApplication.create()
                               .registerComponent(CommandBus.class, c -> testCommandBus);

        testSubject.enhance(configurer);

        CommandBus configuredCommandBus = configurer.build()
                                                    .getComponent(CommandBus.class);

        assertEquals(testCommandBus, configuredCommandBus);
    }

    private static class TestCommandBus implements CommandBus {

        @Override
        public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
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