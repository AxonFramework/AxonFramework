package org.axonframework.springboot.connection;

import org.axonframework.test.server.AxonServerContainer;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

public class AxonServerTestContainerConnectionDetailsFactory extends ContainerConnectionDetailsFactory<AxonServerContainer, AxonServerConnectionDetails> {

    public AxonServerTestContainerConnectionDetailsFactory() {
        super(ContainerConnectionDetailsFactory.ANY_CONNECTION_NAME,
              "org.axonframework.test.server.AxonServerContainer",
              "org.axonframework.axonserver.connector.AxonServerConnectionManager");
    }

    @Override
    protected AxonServerConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<AxonServerContainer> source) {
        return new AxonServerContainerConnectionDetails(source);
    }

    private static final class AxonServerContainerConnectionDetails
            extends ContainerConnectionDetails<AxonServerContainer> implements AxonServerConnectionDetails {

        AxonServerContainerConnectionDetails(ContainerConnectionSource<AxonServerContainer> source) {
            super(source);
        }

        @Override
        public String routingServers() {
            return getContainer().getAxonServerAddress() + ":" + getContainer().getGrpcPort();
        }


    }

}
