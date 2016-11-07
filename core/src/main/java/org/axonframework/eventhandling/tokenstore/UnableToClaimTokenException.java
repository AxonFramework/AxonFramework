package org.axonframework.eventhandling.tokenstore;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that a processor tried to claim a Token (either by retrieving or updating it) that has already
 * been claimed by another process. This typically happens when two processes (JVM's) contain processors with the same
 * name (and potentially the same configuration). In such case, only the first processor can use the TrackingToken.
 * <p>
 * Processes may retry obtaining the claim, preferably after a brief waiting period.
 */
public class UnableToClaimTokenException extends AxonTransientException {

    /**
     * Initialize the exception with given {@code message}.
     *
     * @param message The message describing the exception
     */
    public UnableToClaimTokenException(String message) {
        super(message);
    }

}
