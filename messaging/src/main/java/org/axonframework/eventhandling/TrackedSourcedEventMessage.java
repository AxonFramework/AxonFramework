package org.axonframework.eventhandling;

/**
 * Representing a {@link TrackedEventMessage} wrapped with it's source. A use for this is to make the order in which
 * events are processed in {@code MultiStreamableMessageSource} configurable on their origin
 * @param <T> The type of payload contained in this Message
 * @author Greg Woods
 */
public interface TrackedSourcedEventMessage<T> extends TrackedEventMessage<T> {

    /**
     * returns the source of the event
     * @return the source of the event
     */
    String source();
}
