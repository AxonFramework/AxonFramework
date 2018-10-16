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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.axonframework.common.Assert.nonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Polls {@link Consumer} and inserts records on {@link Buffer}.
 *
 * @author Nakul Mishra
 * @since 3.3
 */
class FetchEventsTask<K, V> implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(FetchEventsTask.class);
    private final Consumer<K, V> consumer;
    private final Buffer<KafkaEventMessage> buffer;
    private final KafkaMessageConverter<K, V> converter;
    private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback;
    private final long timeout;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final java.util.function.Consumer<FetchEventsTask> closeHandler;

    private KafkaTrackingToken currentToken;

    public FetchEventsTask(Consumer<K, V> consumer, KafkaTrackingToken token,
                           Buffer<KafkaEventMessage> buffer,
                           KafkaMessageConverter<K, V> converter,
                           BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback,
                           long timeout,
                           java.util.function.Consumer<FetchEventsTask> closeHandler) {
        Assert.isFalse(timeout < 0, () -> "Timeout may not be < 0");

        this.consumer = nonNull(consumer, () -> "Consumer may not be null");
        this.currentToken = nonNull(token, () -> "Token may not be null");
        this.buffer = nonNull(buffer, () -> "Buffer may not be null");
        this.converter = nonNull(converter, () -> "Converter may not be null");
        this.callback = nonNull(callback, () -> "Callback may not be null");
        this.timeout = timeout;
        this.closeHandler = getOrDefault(closeHandler, x -> {});
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                ConsumerRecords<K, V> records = consumer.poll(timeout);
                if (logger.isDebugEnabled()) {
                    logger.debug("Fetched {} records", records.count());
                }
                Collection<KafkaEventMessage> messages = new ArrayList<>(records.count());
                List<CallbackEntry<K, V>> callbacks = new ArrayList<>(records.count());
                for (ConsumerRecord<K, V> record : records) {
                    converter.readKafkaMessage(record).ifPresent(eventMessage -> {
                        KafkaTrackingToken nextToken = currentToken.advancedTo(record.partition(), record.offset());
                        if (logger.isDebugEnabled()) {
                            logger.debug("Updating token from {} -> {}", currentToken, nextToken);
                        }
                        currentToken = nextToken;
                        messages.add(KafkaEventMessage.from(eventMessage, record, currentToken));
                        callbacks.add(new CallbackEntry<>(currentToken, record));
                    });
                }
                try {
                    buffer.putAll(messages);
                    for (CallbackEntry<K, V> c : callbacks) {
                        this.callback.apply(c.record, c.token);
                    }
                } catch (InterruptedException e) {
                    running.set(false);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Event producer thread was interrupted. Shutting down.", e);
                    }
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.error("Cannot proceed with Fetching, encountered {} ", e);
        } finally {
            running.set(false);
            closeHandler.accept(this);
            consumer.close();
        }
    }

    public void close() {
        this.running.set(false);
    }

    private static class CallbackEntry<K, V> {

        private final KafkaTrackingToken token;
        private final ConsumerRecord<K, V> record;

        public CallbackEntry(KafkaTrackingToken token, ConsumerRecord<K, V> record) {
            this.token = token;
            this.record = record;
        }
    }

}
