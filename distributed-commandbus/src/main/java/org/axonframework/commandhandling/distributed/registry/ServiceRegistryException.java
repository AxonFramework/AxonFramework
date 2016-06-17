package org.axonframework.commandhandling.distributed.registry;

/**
 * This exception will be thrown when an error occures during the publishing and unpublishing of this node on the
 * cluster
 */
public class ServiceRegistryException extends Exception {
    public ServiceRegistryException(String message) {
        super(message);
    }

    public ServiceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
