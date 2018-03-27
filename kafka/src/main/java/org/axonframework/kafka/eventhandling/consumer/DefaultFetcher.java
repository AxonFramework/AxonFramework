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
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.MessageStream;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Nakul Mishra
 */
public class DefaultFetcher implements Fetcher<String, byte[]> {

    private final MessageBuffer<MessageAndMetadata> buffer;
    private final ExecutorService pool;
    private final KafkaMessageConverter<String, byte[]> converter;
    private final Consumer<String, byte[]> consumer;
    private final String topic;
    private Future<?> currentTask;

    public DefaultFetcher(int bufferSize,
                          ConsumerFactory<String, byte[]> consumerFactory,
                          KafkaMessageConverter<String, byte[]> converter, String topic) {

        this.buffer = new MessageBuffer<>();
        this.converter = converter;
        pool = Executors.newSingleThreadExecutor();
        this.topic = topic;
        this.consumer = consumerFactory.createConsumer();
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token) {
        ConsumerUtil.seek(topic, consumer, token);
        if (KafkaTrackingToken.isEmpty(token)) {
            token = KafkaTrackingToken.newInstance(new HashMap<>());
        }
        currentTask = pool.submit(new FetchEventsTask<>(consumer, token, buffer, converter));
        return new KafkaMessageStream(buffer, this);
    }

    @Override
    public void shutdown() {
        currentTask.cancel(true);
        pool.shutdown();
        consumer.close();
    }
}