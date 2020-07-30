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

package org.axonframework.axonserver.connector.heartbeat.connection.checker;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.ConnectionSanityChecker;

/**
 * {@link ConnectionSanityChecker} implementation that verifies if the gRPC channel between client and Axon Server is
 * connected.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class ActiveGrpcChannelChecker implements ConnectionSanityChecker {

    private final AxonServerConnectionManager connectionManager;
    private final String context;

    /**
     * Constructs an {@link ActiveGrpcChannelChecker} based on the connection manager.
     *
     * @param connectionManager the {@link AxonServerConnectionManager}
     * @param context           the (Bounded) Context for which is verified the AxonServer connection through the gRPC
     *                          channel
     */
    public ActiveGrpcChannelChecker(AxonServerConnectionManager connectionManager, String context) {
        this.connectionManager = connectionManager;
        this.context = context;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Detects if exists a gRPC channel between the client an Axon Server.
     *
     * @return {@code true} if the gRPC channel is opened, false otherwise
     */
    @Override
    public boolean isValid() {
        return connectionManager.isConnected(context);
    }
}
