package org.axonframework.axonserver.connector.command;

/**
 * Provides the load factor value of the client for the specific command.
 * This information is used from AxonServer in order to balance the load among client instances.
 *
 * @author Sara Pellegrini
 * @since 4.3
 */
@FunctionalInterface
public interface CommandLoadFactorProvider {

    /**
     * The default value for command load factor: 100.
     * It represents the fixed value of load factor sent to Axon Server for any command's subscription if
     * no specific implementation of CommandLoadFactorProvider is configured.
     */
    int DEFAULT_VALUE = 100;

    /**
     * Returns the load factor value for the specific command
     *
     * @param command the command name
     * @return the load factor value for the specific command
     */
    int getFor(String command);
}
