package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MessageStream;

/**
 * Interface describing the component responsible for reading messages from Kafka.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 * @author Nakul Mishra
 */
public interface Fetcher<K, V> {

    /**
     *
     * @param token
     * @return
     */
    MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token);

    /**
     * Close fetcher.
     */
    void shutdown();
}
