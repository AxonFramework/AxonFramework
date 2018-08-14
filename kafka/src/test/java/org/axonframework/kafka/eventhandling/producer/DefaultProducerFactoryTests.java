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

package org.axonframework.kafka.eventhandling.producer;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.empty;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.minimal;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.minimalTransactional;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.producerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.txnProducerFactory;
import static org.axonframework.kafka.eventhandling.producer.ConfirmationMode.NONE;
import static org.axonframework.kafka.eventhandling.producer.ConfirmationMode.TRANSACTIONAL;
import static org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory.builder;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultProducerFactory}.
 *
 * @author Nakul Mishra
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {
        "testProducerCreation",
        "testSendingMessagesUsingMultipleProducers",
        "testSendingMessagesUsingMultipleTransactionalProducers",
        "testUsingCallbackWhilePublishingMessages",
        "testTransactionalProducerBehaviorOnCommittingAnAbortedTransaction"}, count = 3)
public class DefaultProducerFactoryTests {

    @Autowired
    private KafkaEmbedded kafka;

    @Test
    public void testDefaultConfirmationMode() {
        assertThat(builder(empty()).build().confirmationMode()).isEqualTo(NONE);
    }

    @Test
    public void testDefaultConfirmationMode_ForTransactionalProducer() {
        assertThat(txnProducerFactory(kafka, "foo").confirmationMode()).isEqualTo(TRANSACTIONAL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguring_InvalidCacheSize() {
        builder(minimal(kafka)).withProducerCacheSize(-1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguring_InvalidTimeout() {
        builder(minimal(kafka)).withCloseTimeout(-1, TimeUnit.SECONDS).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguring_InvalidTimeoutUnit() {
        builder(minimal(kafka)).withCloseTimeout(1, null).build();
    }

    @Test
    public void testProducerCreation() {
        ProducerFactory<String, String> pf = producerFactory(kafka);
        Producer<String, String> producer = pf.createProducer();
        assertThat(producer.metrics()).isNotEmpty();
        assertThat(producer.partitionsFor("testProducerCreation")).isNotEmpty();
        cleanup(pf, producer);
    }

    @Test
    public void testCaching_ProducerInstances() {
        ProducerFactory<String, String> pf = producerFactory(kafka);
        List<Producer<String, String>> producers = new ArrayList<>();
        producers.add(pf.createProducer());
        Producer<String, String> original = producers.get(0);
        IntStream.range(0, 10).forEach(x -> {
            Producer<String, String> copy = pf.createProducer();
            assertThat(copy).isEqualTo(original);
            producers.add(copy);
        });
        cleanup(pf, producers);
    }

    @Test
    public void testSendingMessages_UsingMultipleProducers() throws ExecutionException, InterruptedException {
        ProducerFactory<String, String> pf = producerFactory(kafka);
        List<Producer<String, String>> producers = new ArrayList<>();
        List<Future<RecordMetadata>> results = new ArrayList<>();
        String topic = "testSendingMessagesUsingMultipleProducers";
        for (int i = 0; i < 10; i++) {
            Producer<String, String> producer = pf.createProducer();
            results.add(send(producer, topic, "foo" + i));
            producers.add(producer);
        }
        assertOffsets(results);
        cleanup(pf, producers);
    }

    @Test
    public void testTransactionalProducerCreation() {
        Assume.assumeFalse("Transactional producers not supported on Windows",
                           System.getProperty("os.name").contains("Windows"));

        ProducerFactory<String, String> pf = txnProducerFactory(kafka, "xyz");
        Producer<String, String> producer = pf.createProducer();
        producer.beginTransaction();
        producer.commitTransaction();
        assertThat(producer.metrics()).isNotEmpty();
        cleanup(pf, producer);
    }

    @Test
    public void testCaching_TransactionalProducerInstances() {
        ProducerFactory<String, String> pf = txnProducerFactory(kafka, "bar");
        List<Producer<String, String>> producers = new ArrayList<>();
        producers.add(pf.createProducer());
        Producer<String, String> original = producers.get(0);
        IntStream.range(0, 10).forEach(x -> {
            Producer<String, String> copy = pf.createProducer();
            assertThat(copy).isNotEqualTo(original);
        });
        cleanup(pf, producers);
    }

    @Test
    public void testSendingMessages_UsingMultipleTransactionalProducers()
            throws ExecutionException, InterruptedException {
        ProducerFactory<String, String> pf = txnProducerFactory(kafka, "xyz");
        List<Producer<String, String>> producers = new ArrayList<>();
        List<Future<RecordMetadata>> results = new ArrayList<>();
        String topic = "testSendingMessagesUsingMultipleTransactionalProducers";
        for (int i = 0; i < 10; i++) {
            Producer<String, String> producer = pf.createProducer();
            producer.beginTransaction();
            results.add(send(producer, topic, "foo" + i));
            producer.commitTransaction();
            producers.add(producer);
        }
        assertOffsets(results);
        cleanup(pf, producers);
    }

    @Test(expected = KafkaException.class)
    public void testTransactionalProducerBehavior_OnCommittingAnAbortedTransaction() {
        Assume.assumeFalse("Transactional producers not supported on Windows",
                           System.getProperty("os.name").contains("Windows"));

        ProducerFactory<String, String> pf = txnProducerFactory(kafka, "xyz");
        Producer<String, String> producer = pf.createProducer();
        try {
            producer.beginTransaction();
            send(producer, "testTransactionalProducerBehaviorOnCommittingAnAbortedTransaction", "bar");
            producer.abortTransaction();
            producer.commitTransaction();
        } finally {
            cleanup(pf, producer);
        }
    }

    @Test(expected = KafkaException.class)
    public void testTransactionalProducerBehavior_OnSendingOffsetsWhenTransactionIsClosed() {
        Assume.assumeFalse("Transactional producers not supported on Windows",
                           System.getProperty("os.name").contains("Windows"));
        ProducerFactory<String, String> pf = txnProducerFactory(kafka, "xyz");
        Producer<String, String> producer = pf.createProducer();
        producer.beginTransaction();
        producer.commitTransaction();
        producer.sendOffsetsToTransaction(Collections.emptyMap(), "foo");
        cleanup(pf, producer);
    }


    @Test
    public void testClosingProducer_ShouldReturnItToCache() {
        ProducerFactory<Object, Object> pf = builder(minimalTransactional(kafka))
                .withTransactionalIdPrefix("cache")
                .withProducerCacheSize(2)
                .build();
        Producer<Object, Object> first = pf.createProducer();
        first.close();
        Producer<Object, Object> second = pf.createProducer();
        second.close();
        assertThat(second).isEqualTo(first);
        pf.shutDown();
    }

    @Test
    public void testUsingCallback_WhilePublishingMessages() throws ExecutionException, InterruptedException {
        Callback cb = mock(Callback.class);
        ProducerFactory<String, String> pf = producerFactory(kafka);
        Producer<String, String> producer = pf.createProducer();
        producer.send(new ProducerRecord<>("testUsingCallbackWhilePublishingMessages", "callback"), cb).get();
        producer.flush();
        verify(cb, only()).onCompletion(any(RecordMetadata.class), any());
        cleanup(pf, producer);
    }

    private static Future<RecordMetadata> send(Producer<String, String> producer, String topic, String message) {
        Future<RecordMetadata> result = producer.send(new ProducerRecord<>(topic, message));
        producer.flush();
        return result;
    }

    private static void cleanup(ProducerFactory<String, String> pf, Producer<String, String> producer) {
        cleanup(pf, Collections.singletonList(producer));
    }

    private static void cleanup(ProducerFactory<String, String> pf, List<Producer<String, String>> producers) {
        producers.forEach(Producer::close);
        pf.shutDown();
    }

    private static void assertOffsets(List<Future<RecordMetadata>> results) throws InterruptedException, ExecutionException {
        for (Future<RecordMetadata> result : results) {
            assertThat(result.get().offset()).isGreaterThanOrEqualTo(0);
        }
    }
}
