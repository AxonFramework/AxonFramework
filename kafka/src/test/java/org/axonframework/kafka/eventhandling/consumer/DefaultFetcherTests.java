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
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.consumerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.producerFactory;
import static org.axonframework.kafka.eventhandling.consumer.DefaultFetcher.Builder.emptyCallback;
import static org.axonframework.kafka.eventhandling.consumer.DefaultFetcher.builder;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultFetcher}
 *
 * @author Nakul Mishra
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@EmbeddedKafka(topics = {"testStartFetcherWith_ExistingToken_ShouldStartAtSpecificPositions"}, partitions = 5)
public class DefaultFetcherTests {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private KafkaEmbedded kafka;

    private KafkaTrackingToken currentToken;

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderCreation_InvalidTopic() {
        builder(null, mock(ConsumerFactory.class), mock(KafkaMessageConverter.class), mock(SortableBuffer.class), 1);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderCreation_InvalidMessageFactory() {
        builder("foo", null, mock(KafkaMessageConverter.class), mock(SortableBuffer.class), 1);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderCreation_InvalidConverter() {
        builder("foo", mock(ConsumerFactory.class), null, mock(SortableBuffer.class), 1);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderCreation_InvalidBuffer() {
        builder("foo", mock(ConsumerFactory.class), mock(KafkaMessageConverter.class), null, 1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEmptyCallback_ShouldDoNothing() {
        assertThat(emptyCallback().apply(mock(ConsumerRecord.class), mock(KafkaTrackingToken.class))).isNull();
    }

    @Test
    public void testStartFetcherWith_NullToken_ShouldStartFromBeginning() throws InterruptedException {
        CountDownLatch messageCounter = new CountDownLatch(1);
        KafkaTrackingToken endToken = KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 0L);
        }});
        String topic = "foo";
        ConsumerFactory<String, String> cf = mockConsumerFactory(topic);
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>(1);
        Fetcher<String, String> testSubject = DefaultFetcher.builder(topic, cf, new ValueConverter(), buffer, 3000)
                                                            .withPool(newSingleThreadExecutor())
                                                            .onRecordPublished(countMessage(messageCounter))
                                                            .build();

        testSubject.start(null);
        messageCounter.await();

        assertThat(buffer.size()).isOne();
        assertThat(currentToken).isEqualTo(endToken);
        testSubject.shutdown();
    }

    @Test
    public void testStartFetcherWith_ExistingToken_ShouldStartAtSpecificPositions() throws InterruptedException {
        int expectedMessages = 26;
        KafkaTrackingToken endToken = KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 9L);
            put(1, 9L);
            put(2, 9L);
            put(3, 9L);
            put(4, 9L);
        }});
        CountDownLatch messageCounter = new CountDownLatch(expectedMessages);
        String topic = "testStartFetcherWith_ExistingToken_ShouldStartAtSpecificPositions";
        int p0 = 0, p1 = 1, p2 = 2, p3 = 3, p4 = 4;
        ProducerFactory<String, String> pf = publishRecords(topic, p0, p1, p2, p3, p4);
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>(expectedMessages);
        ConsumerFactory<String, String> cf = consumerFactory(kafka, topic);
        Map<Integer, Long> positions = new HashMap<Integer, Long>() {{
            put(0, 5L);
            put(1, 1L);
            put(2, 9L);
            put(3, 4L);
            put(4, 0L);
        }};
        Fetcher<String, String> testSubject = DefaultFetcher.builder(topic, cf, new ValueConverter(), buffer, 3000)
                                                            .onRecordPublished(countMessage(messageCounter))
                                                            .build();
        KafkaTrackingToken startingToken = KafkaTrackingToken.newInstance(positions);

        testSubject.start(startingToken);
        messageCounter.await();

        assertThat(buffer.size()).isEqualTo(expectedMessages);
        assertThat(currentToken).isEqualTo(endToken);
        assertMessagesCountPerPartition(expectedMessages, p0, p1, p2, p3, p4, buffer);
        pf.shutDown();
        testSubject.shutdown();
    }

    @SuppressWarnings("unchecked")
    private static ConsumerFactory<String, String> mockConsumerFactory(String topic) {
        ConsumerFactory<String, String> cf = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        when(cf.createConsumer()).thenReturn(consumer);

        int partition = 0;
        Map<TopicPartition, List<ConsumerRecord<String, String>>> record = new HashMap<>();
        record.put(new TopicPartition(topic, partition), Collections.singletonList(new ConsumerRecord<>(
                topic, partition, 0, null, "hello"
        )));
        ConsumerRecords<String, String> records = new ConsumerRecords<>(record);
        when(consumer.poll(anyLong())).thenReturn(records);

        return cf;
    }

    private BiFunction<ConsumerRecord<String, String>, KafkaTrackingToken, Void> countMessage(
            CountDownLatch counter) {
        return (r, t) -> {
            counter.countDown();
            currentToken = t;
            return null;
        };
    }

    private static void assertMessagesCountPerPartition(int expectedMessages, int p0, int p1, int p2, int p3, int p4,
                                                        MessageBuffer<MessageAndMetadata> buffer)
            throws InterruptedException {
        Map<Integer, Integer> received = new HashMap<>();
        for (int i = 0; i < expectedMessages; i++) {
            MessageAndMetadata m = buffer.take();
            received.putIfAbsent(m.partition(), 0);
            received.put(m.partition(), received.get(m.partition()) + 1);
        }
        assertThat(received.get(p0)).isEqualTo(4);
        assertThat(received.get(p1)).isEqualTo(8);
        assertThat(received.get(p2)).isNull();
        assertThat(received.get(p3)).isEqualTo(5);
        assertThat(received.get(p4)).isEqualTo(9);
    }

    private ProducerFactory<String, String> publishRecords(String topic, int p0, int p1, int p2, int p3, int p4) {
        ProducerFactory<String, String> pf = producerFactory(kafka);
        Producer<String, String> producer = pf.createProducer();
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>(topic, p0, null, null, "foo-" + p0 + "-" + i));
            producer.send(new ProducerRecord<>(topic, p1, null, null, "foo-" + p1 + "-" + i));
            producer.send(new ProducerRecord<>(topic, p2, null, null, "foo-" + p2 + "-" + i));
            producer.send(new ProducerRecord<>(topic, p3, null, null, "foo-" + p3 + "-" + i));
            producer.send(new ProducerRecord<>(topic, p4, null, null, "foo-" + p4 + "-" + i));
        }
        producer.flush();
        return pf;
    }

    static class ValueConverter implements KafkaMessageConverter<String, String> {

        @Override
        public ProducerRecord<String, String> createKafkaMessage(EventMessage<?> eventMessage, String topic) {
            throw new UnsupportedOperationException("unimplemented");
        }

        @Override
        public Optional<EventMessage<?>> readKafkaMessage(ConsumerRecord<String, String> consumerRecord) {
            return Optional.of(asEventMessage(consumerRecord.value()));
        }
    }
}