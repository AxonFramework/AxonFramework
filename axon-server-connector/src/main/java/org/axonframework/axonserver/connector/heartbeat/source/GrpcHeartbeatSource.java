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

package org.axonframework.axonserver.connector.heartbeat.source;

import io.axoniq.axonserver.grpc.control.Heartbeat;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.HeartbeatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC implementation of {@link HeartbeatSource}, which sends to AxonServer a {@link Heartbeat} message.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class GrpcHeartbeatSource implements HeartbeatSource {

    private static final Logger logger = LoggerFactory.getLogger(GrpcHeartbeatSource.class);


    /**
     * Creates a {@link GrpcHeartbeatSource} which uses an {@link AxonServerConnectionManager} to send heartbeats
     * messages to AxonServer.
     *
     * @param connectionManager the {@link AxonServerConnectionManager}
     * @param context           defines the (Bounded) Context for which heartbeats are sent
     */
    public GrpcHeartbeatSource(AxonServerConnectionManager connectionManager, String context) {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Sends a {@link PlatformInboundInstruction} to AxonServer that contains a {@link Heartbeat} message.
     */
    @Override
    public void pulse() {
    }
}
