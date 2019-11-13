package org.axonframework.axonserver.connector.command;

/**
 * @author Sara Pellegrini
 * @since 4.3
 */
public interface CommandLoadFactorProvider {

    int getFor(String command);
}
