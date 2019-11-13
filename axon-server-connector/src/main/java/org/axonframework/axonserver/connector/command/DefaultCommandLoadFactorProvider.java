package org.axonframework.axonserver.connector.command;

/**
 * @author Sara Pellegrini
 * @since 4.3
 */
public class DefaultCommandLoadFactorProvider implements CommandLoadFactorProvider {

    @Override
    public int getFor(String command) {
        return 100;
    }
}
