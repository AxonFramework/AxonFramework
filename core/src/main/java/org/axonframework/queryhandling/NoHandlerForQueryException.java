package org.axonframework.queryhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * @author Marc Gathier
 * @since 3.1
 */
public class NoHandlerForQueryException extends AxonNonTransientException {

    public NoHandlerForQueryException(String message) {
        super(message);
    }
}
