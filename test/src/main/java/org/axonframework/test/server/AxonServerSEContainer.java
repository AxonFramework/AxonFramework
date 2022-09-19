/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.test.server;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

/**
 * Constructs a single node AxonServer Standard Edition (SE) for testing.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class AxonServerSEContainer<SELF extends AxonServerSEContainer<SELF>> extends GenericContainer<SELF> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("axoniq/axonserver");
    private static final int AXON_SERVER_HTTP_PORT = 8024;
    private static final int AXON_SERVER_GRPC_PORT = 8124;

    private static final String WAIT_FOR_LOG_MESSAGE = ".*Started AxonServer.*";

    private static final String AXONIQ_AXONSERVER_DEVMODE_ENABLED = "AXONIQ_AXONSERVER_DEVMODE_ENABLED";

    private static final String AXON_SERVER_ADDRESS_TEMPLATE = "%s:%s";

    private boolean devMode;

    /**
     * Initialize AxonServer SE with a given docker image.
     *
     * @param dockerImageName name of the docker image
     */
    public AxonServerSEContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Initialize AxonServer SE with a given docker image.
     *
     * @param dockerImageName name of the docker image
     */
    public AxonServerSEContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        withExposedPorts(AXON_SERVER_HTTP_PORT, AXON_SERVER_GRPC_PORT);
        waitingFor(Wait.forLogMessage(WAIT_FOR_LOG_MESSAGE, 1));
    }

    @Override
    protected void configure() {
        withEnv(AXONIQ_AXONSERVER_DEVMODE_ENABLED, String.valueOf(devMode));
    }

    /**
     * Initialize AxonServer SE on dev mode. Development mode enables some features for development convenience.
     *
     * @param devMode dev mode. Default value is {@code false}.
     * @return Container itself for fluent API.
     */
    public SELF withDevMode(boolean devMode) {
        this.devMode = devMode;
        return self();
    }

    /**
     * Returns the mapped GRPC port used by this Axon Server container.
     *
     * @return mapped GRPC port.
     */
    public Integer getGrpcPort() {
        return this.getMappedPort(AXON_SERVER_GRPC_PORT);
    }

    /**
     * Returns the Axon Server's address container in a host:port format.
     *
     * @return address in host:port format.
     */
    public String getAxonServerAddress() {
        return String.format(AXON_SERVER_ADDRESS_TEMPLATE,
                             this.getHost(),
                             this.getMappedPort(AXON_SERVER_GRPC_PORT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AxonServerSEContainer<?> that = (AxonServerSEContainer<?>) o;
        return devMode == that.devMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), devMode);
    }

    @Override
    public String toString() {
        return "AxonServerSEContainer{" +
                "devMode=" + devMode +
                '}';
    }
}
