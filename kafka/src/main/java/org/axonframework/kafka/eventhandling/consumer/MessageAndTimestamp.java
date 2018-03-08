package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;
class MessageAndTimestamp implements Comparable<MessageAndTimestamp> {

    private final TrackedEventMessage<?> eventMessage;
    private final long timestamp;

    MessageAndTimestamp(TrackedEventMessage<?> eventMessage, long timestamp) {
        this.eventMessage = eventMessage;
        this.timestamp = timestamp;
    }

    public TrackedEventMessage<?> getEventMessage() {
        return eventMessage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "MessageAndTimestamp{" +
                "eventMessage=" + eventMessage +
                '}';
    }

    @Override
    public int compareTo(MessageAndTimestamp o) {
        return Long.compare(this.timestamp, o.timestamp);
    }
}