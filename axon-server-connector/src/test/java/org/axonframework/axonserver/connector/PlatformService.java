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

import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.control.Heartbeat;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stub platform service to tap into the {@link PlatformInboundInstruction} being sent.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class PlatformService extends PlatformServiceGrpc.PlatformServiceImplBase {

    private final int port;

    private final List<ClientIdentification> clientIdentificationRequests = new CopyOnWriteArrayList<>();
    private final List<Heartbeat> heartbeatMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger completedCounter = new AtomicInteger(0);

    public PlatformService(int port) {
        this.port = port;
    }

    @Override
    public void getPlatformServer(ClientIdentification request, StreamObserver<PlatformInfo> responseObserver) {
        clientIdentificationRequests.add(request);
        responseObserver.onNext(PlatformInfo.newBuilder()
                                            .setPrimary(NodeInfo.newBuilder()
                                                                .setGrpcPort(port)
                                                                .setHostName("localhost")
                                                                .setNodeName("test")
                                                                .setVersion(0)
                                                                .build())
                                            .build());
        responseObserver.onCompleted();
    }

    public List<ClientIdentification> getClientIdentificationRequests() {
        return Collections.unmodifiableList(clientIdentificationRequests);
    }

    public List<Heartbeat> getHeartbeatMessages() {
        return Collections.unmodifiableList(heartbeatMessages);
    }

    public int getNumberOfCompletedStreams() {
        return completedCounter.get();
    }

    @Override
    public StreamObserver<PlatformInboundInstruction> openStream(
            StreamObserver<PlatformOutboundInstruction> responseObserver
    ) {
        return new StreamObserver<PlatformInboundInstruction>() {
            @Override
            public void onNext(PlatformInboundInstruction platformInboundInstruction) {

                if (platformInboundInstruction.hasRegister()) {
                    ClientIdentification register = platformInboundInstruction.getRegister();
                    System.out.println(register);
                    clientIdentificationRequests.add(register);
                } else if (platformInboundInstruction.hasHeartbeat()) {
                    heartbeatMessages.add(platformInboundInstruction.getHeartbeat());
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {
                completedCounter.incrementAndGet();
                responseObserver.onCompleted();
            }
        };
    }
}
