package org.axonframework.eventhandling.replay;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception that indicates that event replays are not supported by a component. Generally these are thrown in the
 * {@link ReplayAware#beforeReplay()} method of a {@link ReplayAware} component.
 *
 * @author Rene de Waele
 * @since 2.4.3
 */
public class EventReplayUnsupportedException extends AxonNonTransientException {

    /**
     * Initialize the exception with the given <code>message</code>.
     *
     * @param message a detailed message of the cause of the exception
     */
    public EventReplayUnsupportedException(String message) {
        super(message);
    }
}
