package org.axonframework.axonserver.connector.command;

/**
 * Provides the load factor value of the client for the specific command.
 * This information is used from AxonServer in order to balance the load among client instances.
 *
 * @author Sara Pellegrini
 * @since 4.3
 */
public interface CommandLoadFactorProvider {

    /**
     * The default value for command load factor.
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
