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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.Objects;
import java.util.Optional;

/**
 * Constructs a single node AxonServer Enterprise Edition (EE) for testing.
 *
 * @author Lucas Campos
 * @since 4.6.0
 * @deprecated In favor of {@link AxonServerContainer} following the merger of Standard and Enterprise edition into a
 * single version.
 */
@Deprecated
public class AxonServerEEContainer<SELF extends AxonServerEEContainer<SELF>> extends GenericContainer<SELF> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("axoniq/axonserver-enterprise");
    private static final int AXON_SERVER_HTTP_PORT = 8024;
    private static final int AXON_SERVER_GRPC_PORT = 8124;

    private static final String WAIT_FOR_LOG_MESSAGE = ".*Started AxonServer.*";

    private static final String LICENCE_DEFAULT_LOCATION = "/axonserver/config/axoniq.license";
    private static final String CONFIGURATION_DEFAULT_LOCATION = "/axonserver/config/axonserver.properties";
    private static final String CLUSTER_TEMPLATE_DEFAULT_LOCATION = "/axonserver/cluster-template.yml";

    private static final String AXONIQ_LICENSE = "AXONIQ_LICENSE";
    private static final String AXONIQ_AXONSERVER_NAME = "AXONIQ_AXONSERVER_NAME";
    private static final String AXONIQ_AXONSERVER_INTERNAL_HOSTNAME = "AXONIQ_AXONSERVER_INTERNAL_HOSTNAME";
    private static final String AXONIQ_AXONSERVER_HOSTNAME = "AXONIQ_AXONSERVER_HOSTNAME";

    private static final String AXON_SERVER_ADDRESS_TEMPLATE = "%s:%s";

    private String licensePath;
    private String configurationPath;
    private String clusterTemplatePath;
    private String axonServerName;
    private String axonServerInternalHostname;
    private String axonServerHostname;

    /**
     * Initialize AxonServer EE with a given docker image.
     *
     * @param dockerImageName name of the docker image
     */
    public AxonServerEEContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Initialize AxonServer EE with a given docker image.
     *
     * @param dockerImageName name of the docker image
     */
    public AxonServerEEContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);

        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);

        withExposedPorts(AXON_SERVER_HTTP_PORT, AXON_SERVER_GRPC_PORT);
        waitingFor(Wait.forLogMessage(WAIT_FOR_LOG_MESSAGE, 1));
        withEnv(AXONIQ_LICENSE, LICENCE_DEFAULT_LOCATION);
    }

    @Override
    protected void configure() {
        optionallyCopyResourceToContainer(LICENCE_DEFAULT_LOCATION, licensePath);
        optionallyCopyResourceToContainer(CONFIGURATION_DEFAULT_LOCATION, configurationPath);
        optionallyCopyResourceToContainer(CLUSTER_TEMPLATE_DEFAULT_LOCATION, clusterTemplatePath);
        withOptionalEnv(AXONIQ_AXONSERVER_NAME, axonServerName);
        withOptionalEnv(AXONIQ_AXONSERVER_HOSTNAME, axonServerHostname);
        withOptionalEnv(AXONIQ_AXONSERVER_INTERNAL_HOSTNAME, axonServerInternalHostname);
    }

    /**
     * Map (effectively replace) directory in Docker with the content of resourceLocation if resource location is not
     * null
     * <p>
     * Protected to allow for changing implementation by extending the class
     *
     * @param pathNameInContainer path in docker
     * @param resourceLocation    relative classpath to resource
     */
    protected void optionallyCopyResourceToContainer(String pathNameInContainer, String resourceLocation) {
        Optional.ofNullable(resourceLocation)
                .map(MountableFile::forClasspathResource)
                .ifPresent(mountableFile -> withCopyFileToContainer(mountableFile, pathNameInContainer));
    }

    /**
     * Set an environment value if the value is present.
     * <p>
     * Protected to allow for changing implementation by extending the class
     *
     * @param key   environment key value, usually a constant
     * @param value environment value to be set
     */
    protected void withOptionalEnv(String key, String value) {
        Optional.ofNullable(value)
                .ifPresent(v -> withEnv(key, value));
    }

    /**
     * Initialize AxonServer EE with a given license.
     *
     * @param licensePath path to the license file.
     * @return Container itself for fluent API.
     */
    public SELF withLicense(String licensePath) {
        this.licensePath = licensePath;
        return self();
    }

    /**
     * Initialize AxonServer EE with a given configuration file.
     *
     * @param configurationPath path to the configuration file.
     * @return Container itself for fluent API.
     */
    public SELF withConfiguration(String configurationPath) {
        this.configurationPath = configurationPath;
        return self();
    }

    /**
     * Initialize AxonServer EE with a given cluster template configuration file.
     *
     * @param clusterTemplatePath path to the cluster template file.
     * @return Container itself for fluent API.
     */
    public SELF withClusterTemplate(String clusterTemplatePath) {
        this.clusterTemplatePath = clusterTemplatePath;
        return self();
    }

    /**
     * Initialize AxonServer EE with a given Axon Server Name.
     *
     * @param axonServerName name of the Axon Server.
     * @return Container itself for fluent API.
     */
    public SELF withAxonServerName(String axonServerName) {
        this.axonServerName = axonServerName;
        return self();
    }

    /**
     * Initialize AxonServer EE with a given Axon Server Internal Hostname.
     *
     * @param axonServerInternalHostname internal hostname of the Axon Server.
     * @return Container itself for fluent API.
     */
    public SELF withAxonServerInternalHostname(String axonServerInternalHostname) {
        this.axonServerInternalHostname = axonServerInternalHostname;
        return self();
    }

    /**
     * Initialize AxonServer EE with a given Axon Server Hostname.
     *
     * @param axonServerHostname hostname of the Axon Server.
     * @return Container itself for fluent API.
     */
    public SELF withAxonServerHostname(String axonServerHostname) {
        this.axonServerHostname = axonServerHostname;
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
        AxonServerEEContainer<?> that = (AxonServerEEContainer<?>) o;
        return Objects.equals(licensePath, that.licensePath)
                && Objects.equals(configurationPath, that.configurationPath)
                && Objects.equals(clusterTemplatePath, that.clusterTemplatePath)
                && Objects.equals(axonServerName, that.axonServerName)
                && Objects.equals(axonServerInternalHostname, that.axonServerInternalHostname)
                && Objects.equals(axonServerHostname, that.axonServerHostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                            licensePath,
                            configurationPath,
                            clusterTemplatePath,
                            axonServerName,
                            axonServerInternalHostname,
                            axonServerHostname);
    }

    @Override
    public String toString() {
        return "AxonServerEEContainer{" +
                "licensePath='" + licensePath + '\'' +
                ", configurationPath='" + configurationPath + '\'' +
                ", clusterTemplatePath='" + clusterTemplatePath + '\'' +
                ", axonServerName='" + axonServerName + '\'' +
                ", axonServerInternalHostname='" + axonServerInternalHostname + '\'' +
                ", axonServerHostname='" + axonServerHostname + '\'' +
                '}';
    }
}
