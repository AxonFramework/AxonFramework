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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * @author Nakul Mishra
 */
class FetchEventsTask<K, V> implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FetchEventsTask.class);
    private static final long DEFAULT_TIMEOUT = 3000;

    private final Consumer<K, V> consumer;
    private final MessageBuffer<MessageAndMetadata> channel;
    private final KafkaMessageConverter<K, V> converter;
    private KafkaTrackingToken token;

    FetchEventsTask(Consumer<K, V> consumer,
                    KafkaTrackingToken token, MessageBuffer<MessageAndMetadata> channel,
                    KafkaMessageConverter<K, V> converter) {
        Assert.isTrue(consumer != null, () -> "Consumer may not be null");
        Assert.isTrue(channel != null, () -> "Buffer may not be null");
        Assert.isTrue(converter != null, () -> "Converter may not be null");
        Assert.isTrue(token != null, () -> "Token may not be null");
        this.consumer = consumer;
        this.token = token;
        this.channel = channel;
        this.converter = converter;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            ConsumerRecords<K, V> records = consumer.poll(DEFAULT_TIMEOUT); //1000, max.poll.records
            if (logger.isDebugEnabled()) {
                logger.debug("Records fetched: {}", records.count());
            }
            for (ConsumerRecord<K, V> record : records) {
                converter.readKafkaMessage(record).ifPresent(m -> {
                    try {
                        token = token.advancedTo(record.partition(), record.offset());
                        TrackedEventMessage<?> msg = asTrackedEventMessage(m, token);
                        channel.put(new MessageAndMetadata(msg, record.partition(),
                                                           record.offset(),
                                                           record.timestamp()
                        ));
                    } catch (InterruptedException e) {
                        logger.warn("Event producer thread was interrupted. Shutting down.", e);
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }
}