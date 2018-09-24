/*
 * Copyright (c) 2018. AxonIQ
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

import io.axoniq.axonserver.grpc.control.*;
import io.grpc.stub.StreamObserver;
import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformServiceGrpc;

/**
 * Author: marc
 */
public class PlatformService extends PlatformServiceGrpc.PlatformServiceImplBase {
    private final int port;

    public PlatformService(int port) {

        this.port = port;
    }

    @Override
    public void getPlatformServer(ClientIdentification request, StreamObserver<PlatformInfo> responseObserver) {
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

    @Override
    public StreamObserver<PlatformInboundInstruction> openStream(StreamObserver<PlatformOutboundInstruction> responseObserver) {
        return new StreamObserver<PlatformInboundInstruction>() {
            @Override
            public void onNext(PlatformInboundInstruction platformInboundInstruction) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
