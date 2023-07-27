package org.springframework.boot.docker.compose.service.connection;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

import java.util.function.Predicate;
/**
 * Dummy implementation of the DockerComposeConnectionDetailsFactory class to allow compilation using JDK 8
 * <p>
 * All methods in this class return null or are no-ops. This class is included for compilation reasons and should never
 * make to a runtime environment.
 */
public abstract class DockerComposeConnectionDetailsFactory<D extends ConnectionDetails> {


    /**
     * Create a new {@link DockerComposeConnectionDetailsFactory} instance.
     * @param connectionName the required connection name
     * @param requiredClassNames the names of classes that must be present
     */
    protected DockerComposeConnectionDetailsFactory(String connectionName, String... requiredClassNames) {
    }

    protected DockerComposeConnectionDetailsFactory(Predicate<DockerComposeConnectionSource> predicate,
                                                    String... requiredClassNames) {
    }

    protected abstract D getDockerComposeConnectionDetails(DockerComposeConnectionSource source);

}
