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
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.axonserver.connector.ErrorCode.UNSUPPORTED_INSTRUCTION;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

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

        assertNotNull(axonServerConnectionManager.getChannel());

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
            axonServerConnectionManager.getChannel();
            fail("Was not expecting to get a connection");
        } catch (AxonServerException e) {
            assertTrue(e.getMessage().contains("connection"));
        }
    }

    @Test
    void testFrameworkVersionSent() {
        String version = "4.2.1";
        AxonServerConfiguration configuration = AxonServerConfiguration.builder().servers("localhost:" + stubServer.getPort()).build();
        AxonServerConnectionManager axonServerConnectionManager =
                AxonServerConnectionManager.builder()
                                           .axonServerConfiguration(configuration)
                                           .axonFrameworkVersionResolver(() -> version)
                                           .build();

        assertNotNull(axonServerConnectionManager.getChannel());

        List<ClientIdentification> clientIdentificationRequests = stubServer.getPlatformService()
                                                                            .getClientIdentificationRequests();
        assertEquals(1, clientIdentificationRequests.size());
        String receivedVersion = clientIdentificationRequests.get(0).getVersion();
        assertEquals(version, receivedVersion);
    }

    @Test
    void unsupportedInstruction() {
        AxonServerConfiguration configuration = AxonServerConfiguration.builder().servers("localhost:" + stubServer.getPort()).build();
        TestStreamObserver<PlatformInboundInstruction> requestStream = new TestStreamObserver<>();
        AxonServerConnectionManager axonServerConnectionManager =
                spy(AxonServerConnectionManager.builder()
                                               .axonServerConfiguration(configuration)
                                               .requestStreamFactory(so -> requestStream)
                                               .build());
        AtomicReference<StreamObserver<PlatformOutboundInstruction>> outboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            outboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<PlatformOutboundInstruction>();
        }).when(axonServerConnectionManager).getPlatformStream(any(), any());

        axonServerConnectionManager.getChannel();

        String instructionId = "instructionId";
        outboundStreamObserverRef.get().onNext(PlatformOutboundInstruction.newBuilder()
                                                                          .setInstructionId(instructionId)
                                                                          .build());
        assertTrue(requestStream.sentMessages()
                                .stream()
                                .anyMatch(inbound -> inbound.getRequestCase()
                                                            .equals(PlatformInboundInstruction.RequestCase.ACK)
                                        && !inbound.getAck().getSuccess()
                                        && inbound.getAck().getError().getErrorCode().equals(UNSUPPORTED_INSTRUCTION.errorCode())
                                        && inbound.getAck().getInstructionId().equals(instructionId)));
    }

    @Test
    void unsupportedInstructionWithoutInstructionId() {
        AxonServerConfiguration configuration = AxonServerConfiguration.builder().servers("localhost:" + stubServer.getPort()).build();
        TestStreamObserver<PlatformInboundInstruction> requestStream = new TestStreamObserver<>();
        AxonServerConnectionManager axonServerConnectionManager =
                spy(AxonServerConnectionManager.builder()
                                               .axonServerConfiguration(configuration)
                                               .requestStreamFactory(so -> requestStream)
                                               .build());
        AtomicReference<StreamObserver<PlatformOutboundInstruction>> outboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            outboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<PlatformOutboundInstruction>();
        }).when(axonServerConnectionManager).getPlatformStream(any(), any());

        axonServerConnectionManager.getChannel();

        outboundStreamObserverRef.get().onNext(PlatformOutboundInstruction.newBuilder().build());
        assertEquals(0, requestStream.sentMessages().size());
    }
}
