/*
 * Copyright to it's respective authors. This class is a dummy-copy of their originals in Spring Framework.
 */

package org.springframework.boot.testcontainers.service.connection;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.testcontainers.containers.Container;

public abstract class ContainerConnectionDetailsFactory<C extends Container<?>, D extends ConnectionDetails> {

    protected static final String ANY_CONNECTION_NAME = null;

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
