/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServerConnectorConfigurerModuleTest {

    @Test
    public void testAxonServerConfiguredInDefaultConfiguration() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .buildConfiguration();

        assertTrue(configuration.eventStore() instanceof AxonServerEventStore);
        assertTrue(configuration.commandBus() instanceof AxonServerCommandBus);
        assertTrue(configuration.queryBus() instanceof AxonServerQueryBus);
        AxonServerConfiguration axonServerConfiguration = configuration.getComponent(AxonServerConfiguration.class);

        assertEquals("localhost", axonServerConfiguration.getServers());
        assertNotNull(axonServerConfiguration.getClientId());
        assertNotNull(axonServerConfiguration.getComponentName());
        assertTrue(axonServerConfiguration.getComponentName().contains(axonServerConfiguration.getClientId()));
    }
}
