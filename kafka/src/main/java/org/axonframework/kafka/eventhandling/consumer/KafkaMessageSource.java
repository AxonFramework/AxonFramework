/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.StreamableMessageSource;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * MessageSource implementation that deserializes incoming messages and forwards them to one or more event processors.
 * <p>
 * Note that the Processors must be subscribed before the MessageListenerContainer is started. Otherwise, messages will
 * be consumed from the Kafka Topic without any processor processing them.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaMessageSource<K, V> implements StreamableMessageSource<TrackedEventMessage<?>> {

    private static final int DEFAULT_BUFFER_SIZE = 10_000;
    private final ConsumerFactory<K, V> consumerFactory;
    private final KafkaMessageConverter<K, V> converter;
    private final String topic;

    public KafkaMessageSource(ConsumerFactory<K, V> consumerFactory, KafkaMessageConverter<K, V> converter,
                              String kafkaTopicName) {
        Assert.notNull(consumerFactory, () -> "Consumer factory may not be null");
        Assert.notNull(converter, () -> "Consumer factory may not be null");
        Assert.notNull(kafkaTopicName, () -> "kafka topic name may not be null");
        this.consumerFactory = consumerFactory;
        this.converter = converter;
        this.topic = kafkaTopicName;
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
        Assert.isTrue(trackingToken == null || trackingToken instanceof KafkaTrackingToken, () -> "Invalid token type");
        return new Streamer().openStream((KafkaTrackingToken) trackingToken);
    }


    class Streamer {
        private final BlockingQueue<MessageAndTimestamp> buffer;

        Streamer() {
            this.buffer = new PriorityBlockingQueue<>(DEFAULT_BUFFER_SIZE,
                                                      Comparator.comparingLong(MessageAndTimestamp::getTimestamp));
        }

        MessageStream<TrackedEventMessage<?>> openStream(KafkaTrackingToken token) {
            Consumer<K, V> consumer = consumerFactory.createConsumer();
            initConsumer(token, consumer);
            if (token == null) {
                token = KafkaTrackingToken.newInstance(new HashMap<>());
            }
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(new FetchEventsTask<>(consumer, token, buffer, converter));
            return new KafkaMessageStream<>(consumer, buffer, executorService);
        }

        private void initConsumer(KafkaTrackingToken token, Consumer<K, V> consumer) {
            if (token == null) {
                consumer.subscribe(Collections.singletonList(topic));
            } else {
                consumer.assign(KafkaTrackingToken.partitions(topic, token));
                token.getPartitionPositions().forEach((partition, offset) -> consumer
                        .seek(KafkaTrackingToken.partition(topic, partition), offset + 1));
            }
        }
    }
}
