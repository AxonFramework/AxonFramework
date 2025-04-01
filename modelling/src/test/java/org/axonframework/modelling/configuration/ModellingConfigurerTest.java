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

import jakarta.annotation.Nullable;
import org.axonframework.configuration.ConfigurerTestSuite;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ModellingConfigurer}.
 *
 * @author Steven van Beelen
 */
class ModellingConfigurerTest extends ConfigurerTestSuite<ModellingConfigurer> {

    @Override
    public ModellingConfigurer testSubject() {
        return testSubject == null ? ModellingConfigurer.create() : testSubject;
    }

    @Override
    public NewConfiguration build() {
        return testSubject().build();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public Class<MessagingConfigurer> delegateType() {
        return MessagingConfigurer.class;
    }

    @Test
    void registerStatefulCommandHandlingModuleAddsAModuleConfiguration() {
        StateBasedEntityBuilder<String, Object> testEntityBuilder =
                StateBasedEntityBuilder.entity(String.class, Object.class)
                                       .loader(c -> (id, context) -> null)
                                       .persister(c -> (id, entity, context) -> null);
        ModuleBuilder<StatefulCommandHandlingModule> statefulCommandHandlingModule =
                StatefulCommandHandlingModule.named("test")
                                             .entities(entityPhase -> entityPhase.entity(testEntityBuilder))
                                             .commandHandlers(commandHandlerPhase -> commandHandlerPhase.commandHandler(
                                                     new QualifiedName(String.class),
                                                     (command, stateManager, context) -> MessageStream.empty().cast()
                                             ));

        List<NewConfiguration> moduleConfigurations =
                testSubject.registerStatefulCommandHandlingModule(statefulCommandHandlingModule)
                           .build()
                           .getModuleConfigurations();

        assertFalse(moduleConfigurations.isEmpty());
        assertEquals(1, moduleConfigurations.size());
    }

    @Test
    void messagingDelegatesTasks() {
        TestComponent result =
                testSubject.messaging(messaging -> messaging.registerComponent(
                                   TestComponent.class,
                                   c -> TEST_COMPONENT
                           ))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }

    @Test
    void applicationDelegatesTasks() {
        TestComponent result =
                testSubject.application(axon -> axon.registerComponent(TestComponent.class, c -> TEST_COMPONENT))
                           .build()
                           .getComponent(TestComponent.class);

        assertEquals(TEST_COMPONENT, result);
    }
}