package org.axonframework.eventhandling;

/**
 * Generic implementation of a {@link TrackedSourcedEventMessage}.
 *
 * @param <T> The type of payload contained in this Message
 */
public class GenericTrackedSourcedEventMessage<T> extends GenericTrackedEventMessage<T>
        implements TrackedSourcedEventMessage<T> {

    private String source;

    /**
     * Construct a {@link GenericTrackedEventMessage} from a {@link TrackedEventMessage} and a source label.
     *
     * @param source              the source of the event
     * @param trackedEventMessage the contents of the event
     */
    public GenericTrackedSourcedEventMessage(String source, TrackedEventMessage trackedEventMessage) {
        super(trackedEventMessage.trackingToken(), trackedEventMessage);
        this.source = source;
    }

    /**
     * @return the source of the event.
     */
    @Override
    public String source() {
        return source;
    }
}
