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

package org.axonframework.test.server;

import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Constructs an Axon Server container for testing.
 * <p>
 * By default, it starts in a single-node configuration.
 *
 * @author Lucas Campos
 * @author Steven van Beelen
 * @author 4.8.0
 */
public class AxonServerContainer extends GenericContainer<AxonServerContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("axoniq/axonserver:latest-dev");

    private static final int AXON_SERVER_HTTP_PORT = 8024;
    private static final int AXON_SERVER_GRPC_PORT = 8124;

    private static final String WAIT_FOR_LOG_MESSAGE = ".*Started AxonServer.*";
    private static final String HEALTH_ENDPOINT = "/actuator/health";

    private static final String LICENCE_DEFAULT_LOCATION = "/axonserver/config/axoniq.license";
    private static final String CONFIGURATION_DEFAULT_LOCATION = "/axonserver/config/axonserver.properties";
    private static final String CLUSTER_TEMPLATE_DEFAULT_LOCATION = "/axonserver/cluster-template.yml";

    private static final String AXONIQ_LICENSE = "AXONIQ_LICENSE";
    private static final String AXONIQ_AXONSERVER_NAME = "AXONIQ_AXONSERVER_NAME";
    private static final String AXONIQ_AXONSERVER_INTERNAL_HOSTNAME = "AXONIQ_AXONSERVER_INTERNAL_HOSTNAME";
    private static final String AXONIQ_AXONSERVER_HOSTNAME = "AXONIQ_AXONSERVER_HOSTNAME";
    private static final String AXONIQ_AXONSERVER_DEVMODE_ENABLED = "AXONIQ_AXONSERVER_DEVMODE_ENABLED";

    private static final String AXON_SERVER_ADDRESS_TEMPLATE = "%s:%s";

    private String licensePath;
    private String configurationPath;
    private String clusterTemplatePath;
    private String axonServerName;
    private String axonServerInternalHostname;
    private String axonServerHostname;
    private boolean devMode;

    /**
     * Initialize an Axon Server {@link GenericContainer test container} using the default image name
     * {@code "axoniq/axonserver"}.
     */
    public AxonServerContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    /**
     * Initialize Axon Server with the given {@code dockerImageName}.
     *
     * @param dockerImageName The name of the Docker image to initialize this test container with.
     */
    public AxonServerContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Initialize Axon Server with the given {@code dockerImageName}.
     *
     * @param dockerImageName The {@link DockerImageName} to initialize this test container with.
     */
    public AxonServerContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        //noinspection resource | ignore from AutoClosable on GenericContainer
        withExposedPorts(AXON_SERVER_HTTP_PORT, AXON_SERVER_GRPC_PORT)
                .withEnv(AXONIQ_LICENSE, LICENCE_DEFAULT_LOCATION)
                .waitingFor(Wait.forLogMessage(WAIT_FOR_LOG_MESSAGE, 1))
                .waitingFor(Wait.forHttp(HEALTH_ENDPOINT).forPort(AXON_SERVER_HTTP_PORT));
    }

    @Override
    protected void configure() {
        optionallyCopyResourceToContainer(LICENCE_DEFAULT_LOCATION, licensePath);
        optionallyCopyResourceToContainer(CONFIGURATION_DEFAULT_LOCATION, configurationPath);
        optionallyCopyResourceToContainer(CLUSTER_TEMPLATE_DEFAULT_LOCATION, clusterTemplatePath);
        withOptionalEnv(AXONIQ_AXONSERVER_NAME, axonServerName);
        withOptionalEnv(AXONIQ_AXONSERVER_HOSTNAME, axonServerHostname);
        withOptionalEnv(AXONIQ_AXONSERVER_INTERNAL_HOSTNAME, axonServerInternalHostname);
        //noinspection resource | ignore from AutoClosable on GenericContainer
        withEnv(AXONIQ_AXONSERVER_DEVMODE_ENABLED, String.valueOf(devMode));
    }

    @Override
    protected void doStart() {
        super.doStart();
        try {
            AxonServerContainerUtils.initCluster(getHost(), getHttpPort(), isShouldBeReused());
        } catch (IOException e) {
            throw new ContainerLaunchException("Axon Server cluster initialization failed.", e);
        }
    }

    /**
     * Map (effectively replace) a directory in Docker with the content of {@code resourceLocation} if the resource
     * location is not {@code null}.
     * <p>
     * Protected to allow for changing implementation by extending the class.
     *
     * @param pathNameInContainer The path in docker.
     * @param resourceLocation    The relative classpath to the resource.
     */
    protected void optionallyCopyResourceToContainer(String pathNameInContainer, String resourceLocation) {
        //noinspection resource | ignore from AutoClosable on GenericContainer
        Optional.ofNullable(resourceLocation)
                .map(MountableFile::forClasspathResource)
                .ifPresent(mountableFile -> withCopyFileToContainer(mountableFile, pathNameInContainer));
    }

    /**
     * Set an environment value if the value is present.
     * <p>
     * Protected to allow for changing implementation by extending the class
     *
     * @param key   Environment value key, usually a constant.
     * @param value Environment value to be set.
     */
    protected void withOptionalEnv(String key, String value) {
        //noinspection resource | ignore from AutoClosable on GenericContainer
        Optional.ofNullable(value)
                .ifPresent(v -> withEnv(key, value));
    }

    /**
     * Initialize this Axon Server test container with a license file retrieved from the given {@code licensePath}.
     *
     * @param licensePath The path to the license file.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withLicense(String licensePath) {
        this.licensePath = licensePath;
        return self();
    }

    /**
     * Initialize this Axon Server test container with a configuration file retrieved from the given
     * {@code configurationPath}.
     *
     * @param configurationPath The path to the configuration file.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withConfiguration(String configurationPath) {
        this.configurationPath = configurationPath;
        return self();
    }

    /**
     * Initialize this Axon Server test container with a cluster template configuration file retrieved from the given
     * {@code clusterTemplatePath}.
     *
     * @param clusterTemplatePath The path to the cluster template file.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withClusterTemplate(String clusterTemplatePath) {
        this.clusterTemplatePath = clusterTemplatePath;
        return self();
    }

    /**
     * Initialize this Axon Server test container with the given {@code axonServerName}.
     *
     * @param axonServerName The name of the Axon Server instance.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withAxonServerName(String axonServerName) {
        this.axonServerName = axonServerName;
        return self();
    }

    /**
     * Initialize this Axon Server test container with the given {@code axonServerInternalHostname}.
     *
     * @param axonServerInternalHostname The internal hostname of the Axon Server instance.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withAxonServerInternalHostname(String axonServerInternalHostname) {
        this.axonServerInternalHostname = axonServerInternalHostname;
        return self();
    }

    /**
     * Initialize this Axon Server test container with the given {@code axonServerHostname}.
     *
     * @param axonServerHostname The hostname of the Axon Server instance.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withAxonServerHostname(String axonServerHostname) {
        this.axonServerHostname = axonServerHostname;
        return self();
    }

    /**
     * Initialize this Axon Server test container with the given {@code devMode}.
     * <p>
     * Development mode enables some features for development convenience. Default value is {@code false}.
     *
     * @param devMode A {@code boolean} dictating whether to enable development mode, yes or no.
     * @return This container itself for fluent API.
     */
    public AxonServerContainer withDevMode(boolean devMode) {
        this.devMode = devMode;
        return self();
    }

    /**
     * Returns the mapped Http port used by this Axon Server container.
     *
     * @return The mapped Http port used by this Axon Server container.
     */
    public Integer getHttpPort() {
        return this.getMappedPort(AXON_SERVER_HTTP_PORT);
    }

    /**
     * Returns the mapped gRPC port used by this Axon Server container.
     *
     * @return The mapped gRPC port used by this Axon Server container.
     */
    public Integer getGrpcPort() {
        return this.getMappedPort(AXON_SERVER_GRPC_PORT);
    }

    /**
     * Returns the container address in a {@code host:port} format.
     *
     * @return The container address in a {@code host:port} format.
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
        AxonServerContainer that = (AxonServerContainer) o;
        return Objects.equals(licensePath, that.licensePath)
                && Objects.equals(configurationPath, that.configurationPath)
                && Objects.equals(clusterTemplatePath, that.clusterTemplatePath)
                && Objects.equals(axonServerName, that.axonServerName)
                && Objects.equals(axonServerInternalHostname, that.axonServerInternalHostname)
                && Objects.equals(axonServerHostname, that.axonServerHostname)
                && Objects.equals(devMode, that.devMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                            licensePath,
                            configurationPath,
                            clusterTemplatePath,
                            axonServerName,
                            axonServerInternalHostname,
                            axonServerHostname,
                            devMode);
    }

    @Override
    public String toString() {
        return "AxonServerContainer{" +
                "licensePath='" + licensePath + '\'' +
                ", configurationPath='" + configurationPath + '\'' +
                ", clusterTemplatePath='" + clusterTemplatePath + '\'' +
                ", axonServerName='" + axonServerName + '\'' +
                ", axonServerInternalHostname='" + axonServerInternalHostname + '\'' +
                ", axonServerHostname='" + axonServerHostname + '\'' +
                ", devMode='" + devMode + '\'' +
                '}';
    }
}
