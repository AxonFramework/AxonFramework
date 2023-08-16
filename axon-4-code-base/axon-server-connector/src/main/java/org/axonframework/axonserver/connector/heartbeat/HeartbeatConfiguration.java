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

package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.source.GrpcHeartbeatSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.ModuleConfiguration;

import java.util.function.Function;

/**
 * Module configuration that defines the components needed to enable heartbeat and monitor the availability of the
 * connection with AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class HeartbeatConfiguration implements ModuleConfiguration {

    private final Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier;

    private final Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier;

    /**
     * Default constructor for {@link HeartbeatConfiguration}, that uses {@link Configuration} in order to retrieve the
     * registered {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     */
    public HeartbeatConfiguration() {
        this(c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }

    /**
     * Creates a {@link HeartbeatConfiguration} using the provided functions to retrieve the {@link
     * AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     *
     * @param connectionManagerSupplier       function to retrieve the {@link AxonServerConnectionManager} from {@link
     *                                        Configuration}
     * @param axonServerConfigurationSupplier function to retrieve the {@link AxonServerConfiguration} from {@link
     *                                        Configuration}
     */
    public HeartbeatConfiguration(
            Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier,
            Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier) {
        this.connectionManagerSupplier = connectionManagerSupplier;
        this.axonServerConfigurationSupplier = axonServerConfigurationSupplier;
    }

    /**
     * Initializes the {@link GrpcHeartbeatSource} component, needed to send heartbeats to AxonServer, any time the
     * client will receive an heartbeat from the server.
     * <p>
     * Initializes the {@link HeartbeatMonitor} component, needed to force a disconnection if the communication between
     * the client and the server is no longer available.
     * <p>
     *
     * @param config the global configuration, providing access to generic components
     */
    @Override
    public void initialize(Configuration config) {
    }
}
