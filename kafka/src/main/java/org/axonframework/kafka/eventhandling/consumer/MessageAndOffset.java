package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;

class MessageAndOffset {

    private final TrackedEventMessage<?> eventMessage;
    private final long offset;
    private final long timestamp;

    MessageAndOffset(TrackedEventMessage<?> eventMessage, long offset, long timestamp) {
        this.eventMessage = eventMessage;
        this.offset = offset;
        this.timestamp = timestamp;
    }

    public TrackedEventMessage<?> getEventMessage() {
        return eventMessage;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "MessageAndOffset{" +
                "eventMessage=" + eventMessage +
                ", offset=" + offset +
                '}';
    }

    long getTimestamp() {
        return timestamp;
    }
}