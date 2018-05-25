/*
 * Copyright (c) 2010-2018. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Utility for {@link Consumer}.
 *
 * @author Nakul Mishra
 */
public class ConsumerUtil {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerUtil.class);

    private ConsumerUtil() {
        //private ctor
    }

    /**
     * Subscribes the {@link Consumer} to a particular {@link org.apache.kafka.common.internals.Topic} and uses {@link
     * KafkaTrackingToken} to update the partitions whenever a re-balance happens.
     *
     * @param topic    the topic.
     * @param consumer the consumer.
     * @param token    the token.
     */
    public static void seek(String topic, Consumer consumer, KafkaTrackingToken token) {
        consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                if (KafkaTrackingToken.isNotEmpty(token)) {
                    logger.debug("Seeking consumer to {}", token);
                    token.partitionPositions().forEach((partition, offset) -> consumer
                            .seek(KafkaTrackingToken.partition(topic, partition), offset + 1));
                }
            }
        });
    }
}
