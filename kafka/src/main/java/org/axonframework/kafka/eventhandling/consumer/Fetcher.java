package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MessageStream;

/**
 * Interface describing the component responsible for reading messages from Kafka.
 *
 * @author Nakul Mishra
 */
public interface Fetcher {

    /**
     * Open a stream of messages, starting at the position indicated by the given {@code token}.
     *
     * @param token the token representing positions of the partition to start from
     * @return a stream providing messages from Kafka
     */
    MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token);

    /**
     * Shuts the fetcher down, closing any resources used by this fetcher.
     */
    void shutdown();
}
