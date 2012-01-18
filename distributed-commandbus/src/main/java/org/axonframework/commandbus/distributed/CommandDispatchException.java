package org.axonframework.commandbus.distributed;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that an error has occurred while trying to dispatch a command to another (potentially remote)
 * segment of the CommandBus.
 * <p/>
 * The dispatcher of the command may assume that the Command has never reached its destination, and has not been
 * handled.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandDispatchException extends AxonTransientException {

    private static final long serialVersionUID = 4587368927394283730L;

    /**
     * Initializes the exception using the given <code>message</code>.
     *
     * @param message The message describing the exception
     */
    public CommandDispatchException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public CommandDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
