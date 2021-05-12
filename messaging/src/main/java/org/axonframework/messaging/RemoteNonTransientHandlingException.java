package org.axonframework.messaging;

import org.axonframework.common.AxonNonTransientException;

import java.util.List;

/**
 * Exception indicating that a non transient error has occurred while remotely handling a message.
 * <p/>
 * The sender of the message <strong>cannot</strong> assume that the message has not been handled.
 *
 * @author Stefan Andjelkovic
 * @since 4.5
 */
public class RemoteNonTransientHandlingException extends AxonNonTransientException {

    private static final long serialVersionUID = 7481427660341420212L;
    private final List<String> exceptionDescriptions;

    /**
     * Initializes the exception using the given {@code exceptionDescription} describing the remote cause-chain.
     *
     * @param exceptionDescription a {@link String} describing the remote exceptions
     */
    public RemoteNonTransientHandlingException(RemoteExceptionDescription exceptionDescription) {
        super("An exception was thrown by the remote message handling component: " + exceptionDescription.toString());
        this.exceptionDescriptions = exceptionDescription.getDescriptions();
    }

    /**
     * Returns a {@link List} of {@link String}s describing the remote exception.
     *
     * @return a {@link List} of {@link String}s describing the remote exception
     */
    public List<String> getExceptionDescriptions() {
        return exceptionDescriptions;
    }
}
