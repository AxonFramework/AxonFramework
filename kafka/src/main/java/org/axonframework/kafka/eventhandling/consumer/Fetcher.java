package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MessageStream;

/**
 * Fetch messages from Kafka.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 * @author Nakul Mishra
 */
public interface Fetcher<K, V> {

    MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token);

    void shutdown();
}
