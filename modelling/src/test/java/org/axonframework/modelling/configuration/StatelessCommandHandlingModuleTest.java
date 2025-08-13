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

package org.axonframework.modelling.configuration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.DefaultMessagingConfigurer;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.utils.EventTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.utils.EventTestUtils.randomString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class validating the {@link StatelessCommandHandlingModule}.
 *
 * @author Steven van Beelen
 */
class StatelessCommandHandlingModuleTest {

    private static final QualifiedName TEST_COMMAND_NAME = new QualifiedName(TestCommand.class);
    private static final QualifiedName ANOTHER_COMMAND_NAME = new QualifiedName(AnotherCommand.class);
    
    private MessagingConfigurer configurer;
    private Configuration config;

    @BeforeEach
    void setUp() {
        configurer = DefaultMessagingConfigurer.create();
    }

    @Nested
    class ModuleBuilding {

        @Test
        void buildsSuccessfullyWithSingleCommandHandler() {
            // given / when
            StatelessCommandHandlingModule result = StatelessCommandHandlingModule.named("test-module")
                    .commandHandler(TEST_COMMAND_NAME, (cmd, context) -> MessageStream.just(
                            new GenericCommandResultMessage<>("success")))
                    .build();

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void buildsSuccessfullyWithMultipleCommandHandlers() {
            // given / when
            StatelessCommandHandlingModule result = StatelessCommandHandlingModule.named("test-module")
                    .commandHandler(TEST_COMMAND_NAME, (cmd, context) -> MessageStream.just(
                            new GenericCommandResultMessage<>("success1")))
                    .commandHandler(ANOTHER_COMMAND_NAME, (cmd, context) -> MessageStream.just(
                            new GenericCommandResultMessage<>("success2")))
                    .build();

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void buildsSuccessfullyWithCommandHandlingComponent() {
            // given / when
            StatelessCommandHandlingModule result = StatelessCommandHandlingModule.named("test-module")
                    .commandHandlingComponent(c -> SimpleCommandHandlingComponent.create("test-component"))
                    .build();

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void buildsSuccessfullyWithAnnotatedCommandHandlingComponent() {
            // given
            TestAnnotatedHandler annotatedHandler = new TestAnnotatedHandler();

            // when
            StatelessCommandHandlingModule result = StatelessCommandHandlingModule.named("test-module")
                    .annotatedCommandHandlingComponent(c -> annotatedHandler)
                    .build();

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void buildsSuccessfullyWithCommandHandlersLambda() {
            // given / when
            StatelessCommandHandlingModule result = StatelessCommandHandlingModule.named("test-module")
                    .commandHandlers(phase -> {
                        phase.commandHandler(TEST_COMMAND_NAME, (cmd, context) -> MessageStream.just(
                                new GenericCommandResultMessage<>("success")));
                    })
                    .build();

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void throwsExceptionForNullModuleName() {
            // given / when / then
            assertThatThrownBy(() -> StatelessCommandHandlingModule.named(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("The module name cannot be null");
        }

        @Test
        void throwsExceptionForNullCommandName() {
            // given / when / then
            assertThatThrownBy(() -> StatelessCommandHandlingModule.named("test")
                    .commandHandler(null, (cmd, context) -> MessageStream.empty()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("The command name cannot be null");
        }

        @Test
        void throwsExceptionForNullCommandHandler() {
            // given / when / then
            assertThatThrownBy(() -> StatelessCommandHandlingModule.named("test")
                    .commandHandler(TEST_COMMAND_NAME, (org.axonframework.commandhandling.CommandHandler) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("The command handler cannot be null");
        }
    }

    @Nested
    class ModuleIntegration {

        @BeforeEach
        void setUp() {
            config = configurer.build();
        }

        @Test
        void registersCommandHandlerWithCommandBus() {
            // given
            AtomicBoolean handlerInvoked = new AtomicBoolean(false);
            
            configurer.registerModule(
                    StatelessCommandHandlingModule.named("test-module")
                            .commandHandler(TEST_COMMAND_NAME, (cmd, context) -> {
                                handlerInvoked.set(true);
                                return MessageStream.just(new GenericCommandResultMessage<>("success"));
                            })
            );
            config = configurer.build();
            config.start();

            CommandBus commandBus = config.getComponent(CommandBus.class);
            CommandMessage<TestCommand> command = new GenericCommandMessage<>(new TestCommand(randomString()));

            // when
            CompletableFuture<CommandResultMessage<Object>> result = commandBus.dispatch(command);

            // then
            assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
            assertTrue(handlerInvoked.get());
            assertThat(result.join().getPayload()).isEqualTo("success");
        }

        @Test
        void registersMultipleCommandHandlersWithCommandBus() {
            // given
            AtomicBoolean handler1Invoked = new AtomicBoolean(false);
            AtomicBoolean handler2Invoked = new AtomicBoolean(false);
            
            configurer.registerModule(
                    StatelessCommandHandlingModule.named("test-module")
                            .commandHandler(TEST_COMMAND_NAME, (cmd, context) -> {
                                handler1Invoked.set(true);
                                return MessageStream.just(new GenericCommandResultMessage<>("success1"));
                            })
                            .commandHandler(ANOTHER_COMMAND_NAME, (cmd, context) -> {
                                handler2Invoked.set(true);
                                return MessageStream.just(new GenericCommandResultMessage<>("success2"));
                            })
            );
            config = configurer.build();
            config.start();

            CommandBus commandBus = config.getComponent(CommandBus.class);

            // when
            CompletableFuture<CommandResultMessage<Object>> result1 = 
                    commandBus.dispatch(new GenericCommandMessage<>(new TestCommand(randomString())));
            CompletableFuture<CommandResultMessage<Object>> result2 = 
                    commandBus.dispatch(new GenericCommandMessage<>(new AnotherCommand(randomString())));

            // then
            assertThat(result1).succeedsWithin(java.time.Duration.ofSeconds(1));
            assertThat(result2).succeedsWithin(java.time.Duration.ofSeconds(1));
            assertTrue(handler1Invoked.get());
            assertTrue(handler2Invoked.get());
            assertThat(result1.join().getPayload()).isEqualTo("success1");
            assertThat(result2.join().getPayload()).isEqualTo("success2");
        }

        @Test
        void registersCommandHandlingComponentWithCommandBus() {
            // given
            configurer.registerModule(
                    StatelessCommandHandlingModule.named("test-module")
                            .commandHandlingComponent(c -> {
                                SimpleCommandHandlingComponent component = SimpleCommandHandlingComponent.create("test-component");
                                component.subscribe(TEST_COMMAND_NAME, (cmd, context) -> MessageStream.just(
                                        new GenericCommandResultMessage<>("component-success")));
                                return component;
                            })
            );
            config = configurer.build();
            config.start();

            CommandBus commandBus = config.getComponent(CommandBus.class);
            CommandMessage<TestCommand> command = new GenericCommandMessage<>(new TestCommand(randomString()));

            // when
            CompletableFuture<CommandResultMessage<Object>> result = commandBus.dispatch(command);

            // then
            assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
            assertThat(result.join().getPayload()).isEqualTo("component-success");
        }
    }

    private static class TestCommand {
        private final String value;

        public TestCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static class AnotherCommand {
        private final String value;

        public AnotherCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static class TestAnnotatedHandler {
        @org.axonframework.commandhandling.CommandHandler
        public String handle(TestCommand command) {
            return "annotated-success";
        }
    }
}