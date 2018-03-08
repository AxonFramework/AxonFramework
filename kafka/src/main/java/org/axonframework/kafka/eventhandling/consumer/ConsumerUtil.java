package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Nakul Mishra
 */
public class ConsumerUtil {

    public static void seek(String topic, Consumer consumer, KafkaTrackingToken token) {
        consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                if (KafkaTrackingToken.isNotEmpty(token)) {
                    token.getPartitionPositions().forEach((partition, offset) -> consumer
                            .seek(KafkaTrackingToken.partition(topic, partition), offset + 1));
                }
            }
        });
    }
}
