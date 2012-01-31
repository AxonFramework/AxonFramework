package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.common.AxonException;

/**
 * @author Allard Buijze
 */
public class MembershipUpdateFailedException extends AxonException {

    public MembershipUpdateFailedException(String message) {
        super(message);
    }

    public MembershipUpdateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
