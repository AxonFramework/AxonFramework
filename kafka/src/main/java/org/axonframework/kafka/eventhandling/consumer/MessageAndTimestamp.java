package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;
class MessageAndTimestamp {

    private final TrackedEventMessage<?> eventMessage;
    private final long timestamp;

    MessageAndTimestamp(TrackedEventMessage<?> eventMessage, long timestamp) {
        this.eventMessage = eventMessage;
        this.timestamp = timestamp;
    }

    public TrackedEventMessage<?> getEventMessage() {
        return eventMessage;
    }

    @Override
    public String toString() {
        return "MessageAndTimestamp{" +
                "eventMessage=" + eventMessage +
                '}';
    }

    long getTimestamp() {
        return timestamp;
    }
}