package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that a node has failed to update its membership details with the other nodes. Typically, this
 * indicates a mismatch between the views on different nodes, causing misdirected commands.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MembershipUpdateFailedException extends AxonException {

    private static final long serialVersionUID = -433655641071800433L;

    /**
     * Initializes the exception using the given <code>message</code>.
     *
     * @param message The message describing the exception
     */
    public MembershipUpdateFailedException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given <code>message</code> and <code>cause</code>.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public MembershipUpdateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
