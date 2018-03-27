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
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;

import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.consumerFactory;
import static org.axonframework.kafka.eventhandling.consumer.KafkaTrackingToken.partition;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/***
 * Tests for {@link ConsumerUtil}
 *
 * @author Nakul Mishra
 */
@EmbeddedKafka(topics = {"testSeekConsumerWithNullToken",
        "testSeekConsumerWithAnEmptyToken",
        "testSeekConsumerWithAnExistingToken"}, count = 3, partitions = 5)
@RunWith(SpringRunner.class)
public class ConsumerUtilTests {

    @Autowired
    private KafkaEmbedded kafka;

    @Test
    public void testSeekConsumerWithNullToken() {
        String topic = "testSeekConsumerWithNullToken";
        Consumer<?, ?> consumer = consumer(topic);
        ConsumerUtil.seek(topic, consumer, null);
        assertOffsets(topic, consumer, 0L);
        consumer.close();
    }

    @Test
    public void testSeekConsumerWithAnEmptyToken() {
        String topic = "testSeekConsumerWithAnEmptyToken";
        Consumer<?, ?> consumer = consumer(topic);
        ConsumerUtil.seek(topic, consumer, KafkaTrackingToken.newInstance(Collections.emptyMap()));
        assertOffsets(topic, consumer, 0L);
        consumer.close();
    }

    @Test
    public void testSeekConsumerWithAnExistingToken() {
        String topic = "testSeekConsumerWithAnExistingToken";
        Consumer<?, ?> consumer = consumer(topic);
        KafkaTrackingToken token = tokensPerTopic();
        ConsumerUtil.seek(topic, consumer, token);

        consumer.partitionsFor(topic)
                .forEach(x -> assertThat(consumer.position(partition(topic, x.partition())),
                                         is(token.getPartitionPositions().get(x.partition()) + 1)));
        consumer.close();
    }

    private KafkaTrackingToken tokensPerTopic() {
        int partitionsPerTopic = kafka.getPartitionsPerTopic();
        KafkaTrackingToken token = KafkaTrackingToken.newInstance(new HashMap<>());
        for (int i = 0; i < partitionsPerTopic; i++) {
            token = token.advancedTo(i, i);
        }
        return token;
    }

    private void assertOffsets(String topic, Consumer<?, ?> consumer, long expectedPosition) {
        consumer.partitionsFor(topic)
                .forEach(x -> {
                    long currentPosition = consumer.position(partition(topic, x.partition()));
                    assertThat(currentPosition, is(expectedPosition));
                });
    }

    private Consumer<?, ?> consumer(String groupName) {
        return consumerFactory(kafka, groupName).createConsumer();
    }
}