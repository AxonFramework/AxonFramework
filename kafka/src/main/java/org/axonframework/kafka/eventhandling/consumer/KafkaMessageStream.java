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
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericTrackedDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;

import java.util.AbstractCollection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Create message stream from a specific kafka topic. Messages are fetch in bulk and stored in an in-memory buffer. We
 * try
 * to introduce some sort
 * and stored them in a local buffer.
 * Consumer position is tracked via {@link KafkaTrackingToken}Records are fetched from kafka and stored in-memory
 * buffer.
 * <p>
 * This is not thread safe
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaMessageStream<K, V> implements TrackingEventStream {

    private KafkaTrackingToken kafkaTrackingToken;
    private final Consumer<K, V> consumer;
    private final KafkaMessageConverter<K, V> converter;
    private TrackedEventMessage<?> peeked;
    private final Map<TopicPartition, LinkedList<MessageAndOffset>> buffer;

    public KafkaMessageStream(KafkaTrackingToken kafkaTrackingToken, Consumer<K, V> consumer,
                              KafkaMessageConverter<K, V> converter) {
        Assert.isTrue(consumer != null, () -> "Consumer cannot be null ");
        Assert.isTrue(converter != null, () -> "Converter cannot be null ");
        this.kafkaTrackingToken = kafkaTrackingToken;
        this.converter = converter;
        this.consumer = consumer;
        buffer = new HashMap<>();
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        if (peeked != null || hasNextAvailable(0, TimeUnit.MILLISECONDS)) {
            return Optional.ofNullable(peeked);
        }
        return Optional.empty();
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit unit) {
        if (peeked != null) {
            return true;
        }
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        ConsumerRecords<K, V> poll;
        if (buffer.isEmpty()) {
            poll = consumer.poll(unit.toMillis(timeout));
        } else if (buffer.values().stream().anyMatch(AbstractCollection::isEmpty)) {
            poll = consumer.poll(0);
        } else {
            poll = null;
        }
        if (poll != null) {
            poll.partitions().forEach(tp -> {
                LinkedList<MessageAndOffset> partitionList = buffer.computeIfAbsent(tp, x -> new LinkedList<>());
                poll.records(tp).forEach(cr -> converter.readKafkaMessage(cr).ifPresent(em -> {
                    partitionList.add(new MessageAndOffset(em, cr.offset()));
                    consumer.pause(Collections.singletonList(tp));
                }));
            });
        }

        TopicPartition smallest = null;
        long smallestTimestamp = Long.MAX_VALUE;
        for (Map.Entry<TopicPartition, LinkedList<MessageAndOffset>> entry : buffer.entrySet()) {
            long messageTimestamp = entry.getValue().peek().eventMessage.getTimestamp().toEpochMilli();
            if (messageTimestamp < smallestTimestamp) {
                smallest = entry.getKey();
                smallestTimestamp = messageTimestamp;
            }
        }
        if (smallest != null) {
            LinkedList<MessageAndOffset> records = buffer.get(smallest);
            MessageAndOffset message = records.poll();
            KafkaTrackingToken newTrackingToken = kafkaTrackingToken == null
                    ? KafkaTrackingToken.newInstance(Collections.singletonMap(smallest.partition(), message.offset + 1))
                    : kafkaTrackingToken.advancedTo(smallest.partition(), message.offset + 1);
            if (message.eventMessage instanceof DomainEventMessage) {
                peeked = new GenericTrackedDomainEventMessage<>(newTrackingToken,
                                                                (DomainEventMessage<?>) message.eventMessage);
            } else {
                peeked = new GenericTrackedEventMessage<>(newTrackingToken, message.eventMessage);
            }
            if (records.isEmpty()) {
                consumer.resume(Collections.singletonList(smallest));
            }
        }
        return peeked != null;
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
        while (peeked == null) {
            hasNextAvailable(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        TrackedEventMessage<?> nextMessage = peeked;
        peeked = null;
        return nextMessage;
    }

    @Override
    public void close() {
        consumer.close();
    }

    @Override
    public String toString() {
        return "KafkaMessageStream{" +
                "consumer=" + consumer +
                ", kafkaTrackingToken=" + kafkaTrackingToken +
                '}';
    }

    private class MessageAndOffset {

        private final EventMessage<?> eventMessage;
        private final long offset;

        private MessageAndOffset(EventMessage<?> eventMessage, long offset) {

            this.eventMessage = eventMessage;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "MessageAndOffset{" +
                    "eventMessage=" + eventMessage +
                    ", offset=" + offset +
                    '}';
        }
    }


    class MyTrackingStream<K, V> implements TrackingEventStream {

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return Optional.empty();
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            return null;
        }

        @Override
        public void close() {

        }
    }
}
