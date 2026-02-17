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

package org.axonframework.modelling.configuration;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.common.util.StubLifecycleRegistry;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurationDefaults;
import org.axonframework.messaging.core.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.core.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.messaging.core.sequencing.NoOpSequencingPolicy;
import org.axonframework.messaging.core.sequencing.SequencingPolicy;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandHandlingModule}.
 *
 * @author Steven van Beelen
 */
class CommandHandlingModuleTest {

    private static final QualifiedName COMMAND_NAME = new QualifiedName(String.class);

    private CommandHandlingModule.SetupPhase setupPhase;
    private CommandHandlingModule.CommandHandlerPhase commandHandlerPhase;

    @BeforeEach
    void setUp() {
        setupPhase = CommandHandlingModule.named("test-subject");
        commandHandlerPhase = setupPhase.commandHandlers();
    }

    @Test
    void nameReturnsModuleName() {
        assertEquals("test-subject", setupPhase.commandHandlers().build().name());
    }

    @Test
    void buildRegistersCommandHandlers() {
        StateBasedEntityModule<String, String> entityModule =
                StateBasedEntityModule.declarative(String.class, String.class)
                                      .loader(c -> (id, context) -> CompletableFuture.completedFuture("instance"))
                                      .persister(c -> (id, entity, context) -> CompletableFuture.completedFuture(null))
                                      .messagingModel((c, b) -> b
                                              .instanceCommandHandler(
                                                      new QualifiedName(String.class),
                                                      (cmd, state, context) -> MessageStream.just(null))
                                              .build())
                                      .entityIdResolver(config -> (message, context) -> "1");

        // Registers default provider registry to remove MessageOriginProvider, thus removing CorrelationDataInterceptor
        // Registers NoOpSequencingPolicy, thus removing the CommandSequencingInterceptor
        // This ensures we keep the SimpleCommandBus, from which we can retrieve the subscription for validation.
        AxonConfiguration configuration = ModellingConfigurer
                .create()
                .componentRegistry(cr -> cr.registerComponent(CorrelationDataProviderRegistry.class,
                                                              c -> new DefaultCorrelationDataProviderRegistry()))
                .componentRegistry(cr -> cr.registerComponent(SequencingPolicy.class,
                                                              MessagingConfigurationDefaults.COMMAND_SEQUENCING_POLICY,
                                                              c -> NoOpSequencingPolicy.instance()))
                .componentRegistry(cr -> cr.registerModule(entityModule))
                .componentRegistry(cr -> cr.registerModule(
                        setupPhase.commandHandlers()
                                  .commandHandler(new QualifiedName(Integer.class),
                                                  (command, context) -> MessageStream.just(
                                                          null))
                                  .build()
                ))
                .start();

        Configuration resultConfig = configuration.getModuleConfiguration("test-subject").orElseThrow();

        Optional<StateManager> optionalStateManager = resultConfig.getOptionalComponent(StateManager.class);
        assertTrue(optionalStateManager.isPresent());

        assertNotNull(optionalStateManager.get().repository(String.class, String.class));

        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        resultConfig.getComponent(CommandBus.class).describeTo(descriptor);

        Map<QualifiedName, CommandHandlingComponent> subscriptions = descriptor.getProperty("subscriptions");
        assertTrue(subscriptions.containsKey(new QualifiedName(Integer.class)));
        assertTrue(subscriptions.containsKey(new QualifiedName(String.class)));
    }

    @Test
    void buildAnnotatedCommandHandlingComponentSucceedsAndRegisters() {
        //noinspection unused
        var myCommandHandlingObject = new Object() {
            @org.axonframework.messaging.commandhandling.annotation.CommandHandler
            public void handle(String command) {
            }
        };

        Configuration resultConfig =
                setupPhase.commandHandlers()
                          .autodetectedCommandHandlingComponent(c -> myCommandHandlingObject)
                          .build()
                          .build(ModellingConfigurer.create().build(), new StubLifecycleRegistry());

        Optional<CommandHandlingComponent> optionalHandlingComponent = resultConfig.getOptionalComponent(
                CommandHandlingComponent.class, "CommandHandlingComponent[test-subject]");
        assertTrue(optionalHandlingComponent.isPresent());
        assertTrue(optionalHandlingComponent.get().supportedCommands().contains(new QualifiedName(String.class)));
    }

    @Test
    void buildModellingConfigurationSucceedsAndRegistersTheModuleWithComponent() {
        //noinspection unused
        var myCommandHandlingObject = new Object() {
            @org.axonframework.messaging.commandhandling.annotation.CommandHandler
            public void handle(String command) {
            }
        };

        Configuration resultConfig =
                ModellingConfigurer.create()
                                   .registerCommandHandlingModule(
                                           setupPhase.commandHandlers()
                                                     .autodetectedCommandHandlingComponent(c -> myCommandHandlingObject)
                                                     .build()
                                   ).build();


        Optional<CommandHandlingComponent> optionalHandlingComponent = resultConfig
                .getModuleConfiguration("test-subject")
                .flatMap(m -> m.getOptionalComponent(
                        CommandHandlingComponent.class, "CommandHandlingComponent[test-subject]"
                ));
        assertTrue(optionalHandlingComponent.isPresent());
        assertTrue(optionalHandlingComponent.get().supportedCommands().contains(new QualifiedName(String.class)));
    }

    @Test
    void namedThrowsNullPointerExceptionForNullModuleName() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> CommandHandlingModule.named(null));
    }

    @Test
    void namedThrowsIllegalArgumentExceptionForEmptyModuleName() {
        assertThrows(IllegalArgumentException.class, () -> CommandHandlingModule.named(""));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandName() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(null, (cmd, context) -> MessageStream.just(null)));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(COMMAND_NAME, (CommandHandler) null));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandNameWithCommandHandler() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> commandHandlerPhase.commandHandler(null, (cmd, context) -> MessageStream.just(null)));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandNameWithCommandHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandler(
                null, c -> (cmd, context) -> null
        ));
    }

    @Test
    void commandHandlerThrowsNullPointerExceptionForNullCommandHandlerBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandler(
                COMMAND_NAME, (ComponentBuilder<CommandHandler>) null
        ));
    }

    @Test
    void commandHandlingComponentThrowsNullPointerExceptionForNullCommandHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.commandHandlingComponent(null));
    }

    @Test
    void annotatedCommandHandlingComponentThrowsNullPointerExceptionForNullCommandHandlingComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> commandHandlerPhase.autodetectedCommandHandlingComponent(null));
    }

    @Test
    void commandHandlingThrowsNullPointerExceptionForNullCommandHandlerPhaseConsumer() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> setupPhase.commandHandlers(null));
    }
}