package org.axonframework.commandhandling;

import org.axonframework.util.AxonException;

/**
 * Indicates that an exception has occurred while handling a command. Typically, this class is used to wrap checked
 * exceptions that have been thrown from a Command Handler while processing an incoming command.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class CommandExecutionException extends AxonException {

    private static final long serialVersionUID = -4864350962123378098L;

    /**
     * Initializes the exception with given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the exception
     * @param cause   The cause of the exception
     */
    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
