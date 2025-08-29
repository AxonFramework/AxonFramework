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

import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.configuration.ApplicationConfigurerTestSuite;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ModellingConfigurer}.
 *
 * @author Steven van Beelen
 */
class ModellingConfigurerTest extends ApplicationConfigurerTestSuite<ModellingConfigurer> {

    @Override
    public ModellingConfigurer createConfigurer() {
        return testSubject == null ? ModellingConfigurer.create() : testSubject;
    }

    @Test
    void messagingDelegatesTasks() {
        TestComponent result =
                testSubject.componentRegistry(cr -> cr.registerComponent(
                                   TestComponent.class,
                                   c -> TEST_COMPONENT
                           ))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void componentRegistryDelegatesTasks() {
        TestComponent result =
                testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void registerEntityModuleAddsAModuleConfiguration() {
        StateBasedEntityModule<String, Object> testEntityBuilder =
                StateBasedEntityModule.declarative(String.class, Object.class)
                                      .loader(c -> (id, context) -> null)
                                      .persister(c -> (id, entity, context) -> null)
                                      .build();

        Configuration configuration = testSubject.registerEntity(testEntityBuilder).build();

        assertThat(configuration.getModuleConfiguration("SimpleStateBasedEntityModule<java.lang.String, java.lang.Object>")).isPresent();
    }

    @Test
    void registerCommandHandlingModuleAddsAModuleConfiguration() {
        ModuleBuilder<CommandHandlingModule> statefulCommandHandlingModule =
                CommandHandlingModule.named("test")
                                     .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                             new QualifiedName(String.class),
                                             (command, context) -> MessageStream.empty().cast()
                                     ));

        Configuration configuration =
                testSubject.registerCommandHandlingModule(statefulCommandHandlingModule)
                           .build();

        assertThat(configuration.getModuleConfiguration("test")).isPresent();
    }
}