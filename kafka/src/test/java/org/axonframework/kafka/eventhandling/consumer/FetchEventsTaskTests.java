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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.TopicPartition;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE;
import static org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST;
import static org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.consumer.KafkaTrackingToken.emptyToken;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link FetchEventsTask}
 *
 * @author Nakul Mishra
 */
public class FetchEventsTaskTests {

    private static final String SOME_TOPIC = "foo";
    private static final int NO_OF_PARTITIONS = 2;
    private static final int TOTAL_MESSAGES = 100;

    private static MockConsumer<String, String> consumer() {
        MockConsumer<String, String> consumer = new MockConsumer<>(EARLIEST);
        adjustOffsets(SOME_TOPIC, NO_OF_PARTITIONS, consumer);
        consumer.assign(partitions(NO_OF_PARTITIONS));
        consumer.seekToBeginning(partitions(NO_OF_PARTITIONS));
        addRecords(consumer);
        return consumer;
    }

    private static void addRecords(MockConsumer<String, String> consumer) {
        for (int i = 0; i < TOTAL_MESSAGES; i++) {
            consumer.addRecord(new ConsumerRecord<>("foo", i % NO_OF_PARTITIONS, i, i,
                                                    NO_TIMESTAMP_TYPE, -1L, NULL_SIZE, NULL_SIZE,
                                                    null, "foo-" + i));
        }
    }

    private static Collection<TopicPartition> partitions(int noOfPartitions) {
        return IntStream.range(0, noOfPartitions)
                        .mapToObj(x -> new TopicPartition("foo", x))
                        .collect(Collectors.toList());
    }

    private static void adjustOffsets(String topic, int noOfPartitions, MockConsumer<String, ?> consumer) {
        consumer.updateBeginningOffsets(new HashMap<TopicPartition, Long>() {{
            for (int i = 0; i < noOfPartitions; i++) {
                put(new TopicPartition(topic, i), 0L);
            }
        }});
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testTaskConstruction_WithInvalidConsumer_ShouldThrowException() {
        new FetchEventsTask<>(null,
                              mock(KafkaTrackingToken.class),
                              mock(Buffer.class),
                              mock(KafkaMessageConverter.class),
                              mock(BiFunction.class), 0,
                              null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testTaskConstruction_WithInvalidBuffer_ShouldThrowException() {
        new FetchEventsTask<>(mock(KafkaConsumer.class),
                              mock(KafkaTrackingToken.class),
                              null,
                              mock(KafkaMessageConverter.class),
                              mock(BiFunction.class), 0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testTaskConstruction_WithInvalidConverter_ShouldThrowException() {
        new FetchEventsTask<>(mock(KafkaConsumer.class),
                              mock(KafkaTrackingToken.class),
                              mock(Buffer.class),
                              null,
                              mock(BiFunction.class), 0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testTaskConstruction_WithInvalidCallback_ShouldThrowException() {
        new FetchEventsTask<>(mock(KafkaConsumer.class),
                              mock(KafkaTrackingToken.class),
                              mock(Buffer.class),
                              mock(KafkaMessageConverter.class),
                              null,
                              0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("unchecked")
    public void testTaskConstruction_WithNegativeTimeout_ShouldThrowException() {
        new FetchEventsTask<>(mock(KafkaConsumer.class),
                              mock(KafkaTrackingToken.class),
                              mock(Buffer.class),
                              mock(KafkaMessageConverter.class),
                              mock(BiFunction.class),
                              -1, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTaskExecution_StartingThreadAndInterrupt_ShouldNotCauseAnyException() {
        SortedKafkaMessageBuffer<KafkaEventMessage> buffer = new SortedKafkaMessageBuffer<>(TOTAL_MESSAGES);
        KafkaMessageConverter<String, String> converter = new AsyncFetcherTests.ValueConverter();
        FetchEventsTask testSubject = new FetchEventsTask<>(consumer(),
                                                            emptyToken(),
                                                            buffer,
                                                            converter,
                                                            (r, t) -> null, 10000, null);
        Thread thread = new Thread(testSubject);
        thread.start();
        thread.interrupt();

        assertThat(buffer.isEmpty()).isTrue();
    }
}
