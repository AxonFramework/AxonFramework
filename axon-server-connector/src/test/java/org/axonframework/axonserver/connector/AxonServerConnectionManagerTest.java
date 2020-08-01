/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.event.StubServer;
import org.axonframework.axonserver.connector.util.TcpUtil;
import org.axonframework.config.TagsConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AxonServerConnectionManager}.
 *
 * @author Milan Savic
 */
class AxonServerConnectionManagerTest {

    private StubServer stubServer;
    private StubServer secondNode;

    @BeforeEach
    void setUp() throws IOException {
        int port1 = TcpUtil.findFreePort();
        int port2 = TcpUtil.findFreePort();
        stubServer = new StubServer(port1, port2);
        secondNode = new StubServer(port2, port2);
        stubServer.start();
        secondNode.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        stubServer.shutdown();
        secondNode.shutdown();
    }

    @Test
    void checkWhetherConnectionPreferenceIsSent() {
        TagsConfiguration tags = new TagsConfiguration(Collections.singletonMap("key", "value"));
        AxonServerConfiguration configuration = AxonServerConfiguration.builder().servers("localhost:" + stubServer.getPort()).build();
        AxonServerConnectionManager axonServerConnectionManager =
                AxonServerConnectionManager.builder()
                                           .axonServerConfiguration(configuration)
                                           .tagsConfiguration(tags)
                                           .build();

        assertNotNull(axonServerConnectionManager.getConnection("default"));

        List<ClientIdentification> clientIdentificationRequests = stubServer.getPlatformService()
                                                                            .getClientIdentificationRequests();
        assertEquals(1, clientIdentificationRequests.size());
        Map<String, String> expectedTags = clientIdentificationRequests.get(0).getTagsMap();
        assertNotNull(expectedTags);
        assertEquals(1, expectedTags.size());
        assertEquals("value", expectedTags.get("key"));

        assertWithin(1,
                     TimeUnit.SECONDS,
                     () -> assertEquals(1, secondNode.getPlatformService().getClientIdentificationRequests().size()));

        List<ClientIdentification> clients = secondNode.getPlatformService().getClientIdentificationRequests();
        Map<String, String> connectionExpectedTags = clients.get(0).getTagsMap();
        assertNotNull(connectionExpectedTags);
        assertEquals(1, connectionExpectedTags.size());
        assertEquals("value", connectionExpectedTags.get("key"));
    }

    @Test
    void testConnectionTimeout() throws IOException, InterruptedException {
        String version = "4.2.1";
        stubServer.shutdown();
        stubServer = new StubServer(TcpUtil.findFreePort(), new PlatformService(TcpUtil.findFreePort()){
            @Override
            public void getPlatformServer(ClientIdentification request, StreamObserver<PlatformInfo> responseObserver) {
                // ignore calls
            }
        });
        stubServer.start();
        AxonServerConfiguration configuration = AxonServerConfiguration.builder()
                .servers("localhost:" + stubServer.getPort()).connectTimeout(50)
                .build();
        AxonServerConnectionManager axonServerConnectionManager =
                AxonServerConnectionManager.builder()
                        .axonServerConfiguration(configuration)
                        .axonFrameworkVersionResolver(() -> version)
                        .build();
        try {
            AxonServerConnection connection = axonServerConnectionManager.getConnection();
            connection.commandChannel();
            assertWithin(2, TimeUnit.SECONDS,
                         () -> assertTrue(connection.isConnectionFailed(), "Was not expecting to get a connection"));
        } catch (AxonServerException e) {
            assertTrue(e.getMessage().contains("connection"));
        }
    }
}
