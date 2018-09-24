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

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.consumer.*;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.axonframework.messaging.MessageStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.minimal;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {"integration"}, partitions = 5, controlledShutdown = true)
public class KafkaIntegrationTest {

    @Autowired
    private KafkaEmbedded kafka;
    private EventBus eventBus;

    @Test
    public void testPublishAndReadMessages() throws Exception {
        eventBus = new SimpleEventBus();
        ProducerFactory<String, byte[]> producerFactory = ProducerConfigUtil.ackProducerFactory(kafka, ByteArraySerializer.class);
        KafkaPublisher<String, byte[]> publisher = new KafkaPublisher<>(KafkaPublisherConfiguration.<String, byte[]>builder()
                                                                                .withProducerFactory(producerFactory)
                                                                                .withTopic("integration")
                                                                                .withMessageSource(eventBus)
                                                                                .build());
        publisher.start();
        ConsumerFactory<String, byte[]> cf = new DefaultConsumerFactory<>(minimal(kafka, "consumer1", ByteArrayDeserializer.class));
        Fetcher fetcher = AsyncFetcher.builder(cf)
                                      .withTopic("integration")
                                      .withPollTimeout(300, TimeUnit.MILLISECONDS)
                                      .build();
        KafkaMessageSource messageSource = new KafkaMessageSource(fetcher);
        MessageStream<TrackedEventMessage<?>> stream1 = messageSource.openStream(null);
        stream1.close();
        MessageStream<TrackedEventMessage<?>> stream2 = messageSource.openStream(null);
        eventBus.publish(asEventMessage("test"));

        // the consumer may need some time to start
        assertTrue(stream2.hasNextAvailable(15, TimeUnit.SECONDS));
        TrackedEventMessage<?> actual = stream2.nextAvailable();
        assertNotNull(actual);

        stream2.close();
        producerFactory.shutDown();
        fetcher.shutdown();
        publisher.shutDown();
    }
}
