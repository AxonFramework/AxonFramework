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

package org.axonframework.config;

import jakarta.inject.Inject;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ConfigurationResourceInjector}.
 */
class ConfigurationResourceInjectorTest {

    private Configuration configuration;
    private ConfigurationResourceInjector testSubject;

    @BeforeEach
    void setUp() {
        configuration = MessagingConfigurer.create().build();
        testSubject = new ConfigurationResourceInjector(configuration);
    }

    @Test
    void injectorHasResource() {
        Saga saga = new Saga();
        testSubject.injectResources(saga);

        assertSame(configuration.getComponent(CommandBus.class), saga.commandBus);
        assertSame(configuration.getComponent(CommandGateway.class), saga.commandGateway);
        assertSame(configuration.getComponent(QueryGateway.class), saga.queryGateway);
        assertNull(saga.inexistent);
    }

    public static class Saga {

        @Inject
        private CommandBus commandBus;

        @Inject
        private String inexistent;

        private CommandGateway commandGateway;
        private QueryGateway queryGateway;

        @Inject
        public void setCommandGateway(CommandGateway commandGateway) {
            this.commandGateway = commandGateway;
        }

        @Inject
        public void setEventStore(QueryGateway queryGateway) {
            this.queryGateway = queryGateway;
        }
    }
}
