/*
 * Copyright (c) 2010-2019. Axon Framework
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

import io.axoniq.axonserver.grpc.control.ClientIdentification;
import org.axonframework.axonserver.connector.connectionpreference.ConnectionPreference;
import org.axonframework.axonserver.connector.connectionpreference.ConnectionProperty;
import org.axonframework.axonserver.connector.event.StubServer;
import org.junit.*;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AxonServerConnectionManager}.
 *
 * @author Milan Savic
 */
public class AxonServerConnectionManagerTest {

    private StubServer stubServer = new StubServer(8124);

    @Before
    public void setUp() throws IOException {
        stubServer.start();
    }

    @After
    public void tearDown() throws InterruptedException {
        stubServer.shutdown();
    }

    @Test
    public void checkWhetherConnectionPreferenceIsSent() {
        ConnectionPreference connectionPreference = new ConnectionPreference();
        ConnectionProperty usRegion = new ConnectionProperty();
        usRegion.setRequired(true);
        usRegion.setValue("US");
        usRegion.setWeight(10);
        connectionPreference.addProperty("region", usRegion);
        AxonServerConfiguration configuration = AxonServerConfiguration.builder()
                                                                       .connectionPreference(connectionPreference)
                                                                       .build();
        AxonServerConnectionManager axonServerConnectionManager = new AxonServerConnectionManager(configuration);

        assertNotNull(axonServerConnectionManager.getChannel());

        List<ClientIdentification> clientIdentificationRequests = stubServer.getPlatformService()
                                                                            .getClientIdentificationRequests();
        assertEquals(1, clientIdentificationRequests.size());
        io.axoniq.axonserver.grpc.control.ConnectionPreference expectedConnectionPreference =
                clientIdentificationRequests.get(0).getConnectionPreference();
        assertNotNull(expectedConnectionPreference);
        assertEquals(1, expectedConnectionPreference.getPropertiesCount());
        assertTrue(expectedConnectionPreference.containsProperties("region"));
        io.axoniq.axonserver.grpc.control.ConnectionProperty region =
                expectedConnectionPreference.getPropertiesMap().get("region");
        assertTrue(region.getRequired());
        assertEquals("US", region.getValue());
        assertEquals(10, region.getWeight());
    }
}
