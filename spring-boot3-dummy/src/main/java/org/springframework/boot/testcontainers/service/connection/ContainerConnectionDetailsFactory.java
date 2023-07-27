package org.springframework.boot.testcontainers.service.connection;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.testcontainers.containers.Container;

public abstract class ContainerConnectionDetailsFactory<C extends Container<?>, D extends ConnectionDetails> {

    protected ContainerConnectionDetailsFactory(String connectionName, String... requiredClassNames) {

    }

    protected abstract D getContainerConnectionDetails(ContainerConnectionSource<C> source);
    protected static class ContainerConnectionDetails<C extends Container<?>> implements ConnectionDetails {
        protected ContainerConnectionDetails(ContainerConnectionSource<C> source) {

        }

        protected C getContainer() {
            return null;
        }
    }

}
