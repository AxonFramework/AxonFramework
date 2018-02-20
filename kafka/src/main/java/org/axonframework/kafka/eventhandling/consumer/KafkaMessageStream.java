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
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

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
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageStream.class);

    private KafkaTrackingToken kafkaTrackingToken;
    private final Consumer<K, V> consumer;
    private final KafkaMessageConverter<K, V> converter;

    private BufferedEventStream eventConsumer;

    public KafkaMessageStream(KafkaTrackingToken kafkaTrackingToken, Consumer<K, V> consumer, KafkaMessageConverter<K, V> converter) {
        Assert.isTrue(consumer != null, () -> "Consumer cannot be null ");
        Assert.isTrue(converter != null, () -> "Converter cannot be null ");
        this.kafkaTrackingToken = kafkaTrackingToken;
        this.converter = converter;
        this.consumer = consumer;
        eventConsumer = new BufferedEventStream(1_000);
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        return eventConsumer.peek();
    }



    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit unit) {

        if (!eventConsumer.hasNextAvailable(1, TimeUnit.MICROSECONDS)) {
            ConsumerRecords<K, V> poll = consumer.poll(unit.toMillis(timeout));

            StreamSupport.stream(Spliterators.spliteratorUnknownSize(poll.iterator(), Spliterator.ORDERED), false)
                    .map(cr -> converter.readKafkaMessage(cr)
                            //TODO: add proper tracking token instead of null
                            .map(em -> new MessageAndOffset((asTrackedEventMessage(em, null)), cr.offset(), cr.timestamp())))
                    .forEach(e -> eventConsumer.addEvent(e.get()));
        }

        return eventConsumer.hasNextAvailable(timeout, unit);

    }

    @Override
    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
        return eventConsumer.nextAvailable();
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

        private final TrackedEventMessage<?> eventMessage;
        private final long offset;
        final long timestamp;

        private MessageAndOffset(TrackedEventMessage<?> eventMessage, long offset, long timestamp) {
            this.eventMessage = eventMessage;
            this.offset = offset;
            this.timestamp = timestamp;
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


    private class BufferedEventStream implements TrackingEventStream {

        private final BlockingQueue<MessageAndOffset> eventQueue;
        private MessageAndOffset peekEvent;

        private BufferedEventStream(int queueCapacity) {
            eventQueue = new PriorityBlockingQueue<>(queueCapacity, Comparator.comparingLong(MessageAndOffset::getTimestamp));
        }

        private void addEvent(MessageAndOffset event) {

            try {
                eventQueue.put(event);
            } catch (InterruptedException e) {
                logger.warn("Event producer thread was interrupted. Shutting down.", e);
                Thread.currentThread().interrupt();
            }
        }
//TODO: remove if not sued
//        private void addEvents(List<? extends MessageAndOffset> events) {
//            //add one by one because bulk operations on LinkedBlockingQueues are not thread-safe
//            events.forEach(eventMessage -> {
//                try {
//                    eventQueue.put(eventMessage);
//                } catch (InterruptedException e) {
//                    logger.warn("Event producer thread was interrupted. Shutting down.", e);
//                    Thread.currentThread().interrupt();
//                }
//            });
//        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return Optional.ofNullable(peekEvent == null && !hasNextAvailable() ? null : peekEvent.eventMessage);
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) {
            try {
                return peekEvent != null || (peekEvent = eventQueue.poll(timeout, unit)) != null;
            } catch (InterruptedException e) {
                logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            try {
                return peekEvent == null ? eventQueue.take().eventMessage : peekEvent.eventMessage;
            } catch (InterruptedException e) {
                logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
                Thread.currentThread().interrupt();
                return null;
            } finally {
                peekEvent = null;
            }
        }

        @Override
        public void close() {
//            eventStreams.remove(this);
        }
    }

}
