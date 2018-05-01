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
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Polls {@link Consumer} and inserts records on {@link SortableBuffer}.
 *
 * @author Nakul Mishra
 */
class FetchEventsTask<K, V> implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FetchEventsTask.class);
    private final Consumer<K, V> consumer;
    private final SortableBuffer<MessageAndMetadata> buffer;
    private final KafkaMessageConverter<K, V> converter;
    private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback;
    private final long timeout;

    private KafkaTrackingToken currentToken;

    private FetchEventsTask(Builder<K, V> config) {
        this.consumer = config.consumer;
        this.currentToken = config.currentToken;
        this.buffer = config.buffer;
        this.converter = config.converter;
        this.callback = config.callback;
        this.timeout = config.timeout;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<K, V> records = consumer.poll(timeout);
                if (logger.isDebugEnabled()) {
                    logger.debug("Fetched {} records", records.count());
                }
                Collection<MessageAndMetadata> messages = new ArrayList<>(records.count());
                List<CallbackEntry<K, V>> callbacks = new ArrayList<>(records.count());
                for (ConsumerRecord<K, V> record : records) {
                    converter.readKafkaMessage(record).ifPresent(eventMessage -> {
                        KafkaTrackingToken nextToken = currentToken.advancedTo(record.partition(), record.offset());
                        if (logger.isDebugEnabled()) {
                            logger.debug("Updating token from {} -> {}", currentToken, nextToken);
                        }
                        currentToken = nextToken;
                        messages.add(MessageAndMetadata.from(eventMessage, record, currentToken));
                        callbacks.add(new CallbackEntry<>(currentToken, record));
                    });
                }
                try {
                    buffer.putAll(messages);
                    for (CallbackEntry<K, V> c : callbacks) {
                        this.callback.apply(c.record, c.token);
                    }
                } catch (InterruptedException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Event producer thread was interrupted. Shutting down.", e);
                    }
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.error("Cannot proceed with Fetching, encountered {} ", e);
        } finally {
            consumer.close();
        }
    }

    private static class CallbackEntry<K, V> {

        private final KafkaTrackingToken token;
        private final ConsumerRecord<K, V> record;

        public CallbackEntry(KafkaTrackingToken token, ConsumerRecord<K, V> record) {
            this.token = token;
            this.record = record;
        }
    }

    public static <K, V> Builder<K, V> builder(Consumer<K, V> consumer, KafkaTrackingToken token,
                                               SortableBuffer<MessageAndMetadata> buffer,
                                               KafkaMessageConverter<K, V> converter,
                                               BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback,
                                               long timeout) {
        return new Builder<>(consumer, token, buffer, converter, callback, timeout);
    }

    public static class Builder<K, V> {

        private final long timeout;
        private final Consumer<K, V> consumer;
        private final SortableBuffer<MessageAndMetadata> buffer;
        private final KafkaMessageConverter<K, V> converter;
        private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback;
        private final KafkaTrackingToken currentToken;

        public Builder(Consumer<K, V> consumer, KafkaTrackingToken token,
                       SortableBuffer<MessageAndMetadata> buffer,
                       KafkaMessageConverter<K, V> converter,
                       BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback, long timeout) {
            Assert.notNull(consumer, () -> "Consumer may not be null");
            Assert.notNull(buffer, () -> "Buffer may not be null");
            Assert.notNull(converter, () -> "Converter may not be null");
            Assert.notNull(token, () -> "Token may not be null");
            Assert.notNull(callback, () -> "Callback may not be null");
            Assert.isFalse(timeout < 0, () -> "Timeout may not be < 0");
            this.consumer = consumer;
            this.currentToken = token;
            this.buffer = buffer;
            this.converter = converter;
            this.callback = callback;
            this.timeout = timeout;
        }

        @Override
        public String toString() {
            return "Config{" +
                    "consumer=" + consumer +
                    ", buffer=" + buffer +
                    ", converter=" + converter +
                    ", callback=" + callback +
                    ", currentToken=" + currentToken +
                    '}';
        }

        public FetchEventsTask<K, V> build() {
            return new FetchEventsTask<>(this);
        }
    }
}