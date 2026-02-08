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

package org.axonframework.messaging.core.configuration;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleSpecificProcessingContextTest {

    @Test
    void processingContextResolvesModuleSpecificComponent() {
        TestComponent rootComponent = new TestComponent("root");
        TestComponent moduleComponent = new TestComponent("module");
        AtomicReference<TestComponent> resolved = new AtomicReference<>();

        CommandHandlingModule.SetupPhase module = CommandHandlingModule.named("test-module");
        ((org.axonframework.common.configuration.BaseModule<?>) module).componentRegistry(
                moduleRegistry -> moduleRegistry.registerComponent(TestComponent.class, c -> moduleComponent)
        );

        MessagingConfigurer configurer = MessagingConfigurer.create()
                .componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> rootComponent))
                .registerCommandHandlingModule(
                        module.commandHandlers(handlers -> handlers.commandHandler(
                                new QualifiedName(TestCommand.class),
                                (command, context) -> {
                                    resolved.set(context.component(TestComponent.class));
                                    return MessageStream.empty().cast();
                                }
                        ))
                );

        AxonConfiguration configuration = (AxonConfiguration) configurer.build();
        CommandMessage command = new GenericCommandMessage(new MessageType(TestCommand.class), new TestCommand());

        try {
            configuration.start();
            configuration
                    .getComponent(CommandBus.class)
                    .dispatch(command, null)
                    .join();
        } finally {
            configuration.shutdown();
        }

        assertThat(resolved.get()).isSameAs(moduleComponent);
    }

    private static class TestCommand {
    }

    private static class TestComponent {
        private final String name;

        private TestComponent(String name) {
            this.name = name;
        }
    }
}
