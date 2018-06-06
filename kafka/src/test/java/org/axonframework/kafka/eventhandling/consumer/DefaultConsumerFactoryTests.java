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
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.minimal;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.producerFactory;

/**
 * Tests for {@link org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory}
 *
 * @author Nakul Mishra
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {"testCreatedConsumer_ValidConfig_CanCommunicateToKafka"}, partitions = 1)
public class DefaultConsumerFactoryTests {

    @Autowired
    private KafkaEmbedded kafka;

    @Test(expected = IllegalArgumentException.class)
    public void testCreateConsumer_InvalidConfig() {
        new DefaultConsumerFactory<>(null);
    }

    @Test
    public void testCreatedConsumer_ValidConfig_CanCommunicateToKafka() {
        String topic = "testCreatedConsumer_ValidConfig_CanCommunicateToKafka";
        ProducerFactory<String, String> pf = producerFactory(kafka);
        Producer<String, String> producer = pf.createProducer();
        producer.send(new ProducerRecord<>(topic, 0, null, null, "foo"));
        producer.flush();
        DefaultConsumerFactory<Object, Object> testSubject = new DefaultConsumerFactory<>(minimal(kafka, topic));
        Consumer<Object, Object> consumer = testSubject.createConsumer();
        consumer.subscribe(Collections.singleton(topic));

        assertThat(KafkaTestUtils.getRecords(consumer).count()).isOne();

        consumer.close();
        pf.shutDown();
    }
}