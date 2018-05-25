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
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import scala.collection.Seq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyMap;
import static kafka.utils.TestUtils.pollUntilAtLeastNumRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.consumerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.producerFactory;
import static org.axonframework.kafka.eventhandling.consumer.ConsumerUtil.seek;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getRecords;

/***
 * Tests for {@link ConsumerUtil}
 *
 * @author Nakul Mishra
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {"testSeekUsing_NullToken_ConsumerStartsAtPositionZero",
        "testSeekUsing_EmptyToken_ConsumerStartsAtPositionZero",
        "testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition",
        "testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition_AndCanContinueReadingNewRecords"}, partitions = 5)
public class ConsumerUtilTests {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private KafkaEmbedded kafka;

    private ProducerFactory<String, String> pf;

    @Before
    public void setUp() {
        pf = producerFactory(kafka);
    }

    @Test
    public void testSeekUsing_NullToken_ConsumerStartsAtPositionZero() {
        String topic = "testSeekUsing_NullToken_ConsumerStartsAtPositionZero";
        int noOfPartitions = kafka.getPartitionsPerTopic();
        int recordsPerPartitions = 1;
        AtomicInteger count = new AtomicInteger();
        publishRecordsOnMultiplePartitions(topic, recordsPerPartitions);
        Consumer<?, ?> testSubject = consumerFactory(kafka, topic).createConsumer();

        seek(topic, testSubject, null);

        getRecords(testSubject).forEach(r -> {
            assertThat(r.offset()).isZero();
            count.getAndIncrement();
        });
        assertThat(count.get()).isEqualTo(noOfPartitions * recordsPerPartitions);

        testSubject.close();
    }

    @Test
    public void testSeekUsing_EmptyToken_ConsumerStartsAtPositionZero() {
        String topic = "testSeekUsing_EmptyToken_ConsumerStartsAtPositionZero";
        int noOfPartitions = kafka.getPartitionsPerTopic();
        int recordsPerPartitions = 1;
        AtomicInteger count = new AtomicInteger();
        publishRecordsOnMultiplePartitions(topic, recordsPerPartitions);
        Consumer<?, ?> testSubject = consumerFactory(kafka, topic).createConsumer();

        seek(topic, testSubject, KafkaTrackingToken.newInstance(emptyMap()));

        getRecords(testSubject).forEach(r -> {
            assertThat(r.offset()).isZero();
            count.getAndIncrement();
        });
        assertThat(count.get()).isEqualTo(noOfPartitions * recordsPerPartitions);

        testSubject.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition() {
        String topic = "testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition";
        int recordsPerPartitions = 10;
        publishRecordsOnMultiplePartitions(topic, recordsPerPartitions);
        Map<Integer, Long> positions = new HashMap<>();
        positions.put(0, 5L);
        positions.put(1, 1L);
        positions.put(2, 9L);
        positions.put(3, 4L);
        positions.put(4, 0L);
        Consumer<?, ?> testSubject = consumerFactory(kafka, topic).createConsumer();

        seek(topic, testSubject, KafkaTrackingToken.newInstance(positions));
        Seq<ConsumerRecord<byte[], byte[]>> records = pollUntilAtLeastNumRecords((KafkaConsumer<byte[], byte[]>) testSubject,
                                                                                 26);
        records.foreach(r -> assertThat(r.offset()).isGreaterThan(positions.get(r.partition())));
        assertThat(records.count(x -> true)).isEqualTo(26);

        testSubject.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition_AndCanContinueReadingNewRecords() {
        String topic = "testSeekUsing_ExistingToken_ConsumerStartsAtSpecificPosition_AndCanContinueReadingNewRecords";
        int recordsPerPartitions = 10;
        publishRecordsOnMultiplePartitions(topic, recordsPerPartitions);
        Map<Integer, Long> positions = new HashMap<>();
        positions.put(0, 5L);
        positions.put(1, 1L);
        positions.put(2, 9L);
        positions.put(3, 4L);
        positions.put(4, 0L);
        Consumer<?, ?> testSubject = consumerFactory(kafka, topic).createConsumer();
        seek(topic, testSubject, KafkaTrackingToken.newInstance(positions));
        pollUntilAtLeastNumRecords((KafkaConsumer<byte[], byte[]>) testSubject, 26);

        publishNewRecords(topic, pf);
        Seq<ConsumerRecord<byte[], byte[]>> records = pollUntilAtLeastNumRecords((KafkaConsumer<byte[], byte[]>) testSubject,
                                                                                 4);

        records.foreach(x -> assertThat(x.offset()).isEqualTo(10));
        assertThat(records.count(x -> true)).isEqualTo(4);

        testSubject.close();
    }

    @After
    public void tearDown() {
        pf.shutDown();
    }

    private ProducerFactory<String, String> publishRecordsOnMultiplePartitions(String topic, int recordsPerPartitions) {
        Producer<String, String> producer = pf.createProducer();
        for (int i = 0; i < recordsPerPartitions; i++) {
            for (int p = 0; p < kafka.getPartitionsPerTopic(); p++) {
                producer.send(new ProducerRecord<>(topic, p, null, null, "foo"));
            }
        }
        producer.flush();
        return pf;
    }

    private static void publishNewRecords(String topic, ProducerFactory<String, String> pf) {
        Producer<String, String> producer = pf.createProducer();
        producer.send(new ProducerRecord<>(topic, 0, null, null, "bar"));
        producer.send(new ProducerRecord<>(topic, 1, null, null, "bar"));
        producer.send(new ProducerRecord<>(topic, 2, null, null, "bar"));
        producer.send(new ProducerRecord<>(topic, 3, null, null, "bar"));
        producer.flush();
    }
}