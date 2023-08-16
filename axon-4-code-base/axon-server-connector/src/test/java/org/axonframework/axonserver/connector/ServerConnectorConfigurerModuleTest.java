/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ServerConnectorConfigurerModule}.
 *
 * @author Allard Buijze
 */
class ServerConnectorConfigurerModuleTest {

    @Test
    void axonServerConfiguredInDefaultConfiguration() {
        Configuration testSubject = DefaultConfigurer.defaultConfiguration()
                                                     .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                                     .buildConfiguration();

        AxonServerConfiguration resultAxonServerConfig = testSubject.getComponent(AxonServerConfiguration.class);

        assertEquals("localhost", resultAxonServerConfig.getServers());
        assertNotNull(resultAxonServerConfig.getClientId());
        assertNotNull(resultAxonServerConfig.getComponentName());
        assertTrue(resultAxonServerConfig.getComponentName().contains(resultAxonServerConfig.getClientId()));

        assertNotNull(testSubject.getComponent(AxonServerConnectionManager.class));
        assertTrue(
                testSubject.getModules().stream()
                           .anyMatch(moduleConfig -> moduleConfig.isType(EventProcessorInfoConfiguration.class))
        );
        assertTrue(testSubject.eventStore() instanceof AxonServerEventStore);
        assertTrue(testSubject.commandBus() instanceof AxonServerCommandBus);
        assertTrue(testSubject.queryBus() instanceof AxonServerQueryBus);

        //noinspection unchecked
        TargetContextResolver<Message<?>> resultTargetContextResolver =
                testSubject.getComponent(TargetContextResolver.class);
        assertNotNull(resultTargetContextResolver);
        // The default TargetContextResolver is a no-op which returns null
        assertNull(resultTargetContextResolver.resolveContext(GenericEventMessage.asEventMessage("some-event")));
    }

    @Test
    void queryUpdateEmitterIsTakenFromConfiguration() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                                       .buildConfiguration();

        assertTrue(configuration.queryBus() instanceof AxonServerQueryBus);
        assertSame(configuration.queryBus().queryUpdateEmitter(), configuration.queryUpdateEmitter());
        assertSame(((AxonServerQueryBus) configuration.queryBus()).localSegment().queryUpdateEmitter(),
                   configuration.queryUpdateEmitter());
    }

    @Test
    void customCommandLoadFactorProvider() throws NoSuchFieldException {
        CommandLoadFactorProvider expected = command -> 5000;
        Configuration config =
                DefaultConfigurer.defaultConfiguration()
                                 .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                 .registerComponent(CommandLoadFactorProvider.class, c -> expected)
                                 .buildConfiguration();

        CommandBus commandBus = config.commandBus();
        assertTrue(commandBus instanceof AxonServerCommandBus);
        CommandLoadFactorProvider result = ReflectionUtils.getFieldValue(
                AxonServerCommandBus.class.getDeclaredField("loadFactorProvider"), commandBus
        );
        assertEquals(expected, result);
    }
}
