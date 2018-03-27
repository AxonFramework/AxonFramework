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
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.MetaData;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.mockito.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;

/**
 * Tests for {@link FetchEventsTask}
 *
 * @author Nakul Mishra
 */
public class FetchEventsTaskTests {

    private static final String SOME_TOPIC = "foo";
    private static final int NO_OF_PARTITIONS = 5;
    private static final int TOTAL_MESSAGES = 100;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Test
    public void test() {
        int size = 10;
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>(size);
        KafkaMessageConverter<String, byte[]> converter = converter();
        Future<?> future = pool.submit(new FetchEventsTask<>(consumer(), emptyToken(), buffer, converter));
        try {
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        future.cancel(true);
        throw new UnsupportedOperationException("fix me ");
//        assertThat(buffer.size(), CoreMatchers.is(size));
    }

    private KafkaMessageConverter<String, byte[]> converter() {
        KafkaMessageConverter<String, byte[]> converter = Mockito.mock(DefaultKafkaMessageConverter.class);
        Mockito.when(converter.readKafkaMessage(any())).thenReturn(Optional.of(domainMessage("foo-1")));
        return converter;
    }

    private MockConsumer<String, byte[]> consumer() {
        MockConsumer<String, byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        updateBeginningOffsets(SOME_TOPIC, NO_OF_PARTITIONS, consumer);
        consumer.assign(partitions(NO_OF_PARTITIONS));
        consumer.seekToBeginning(partitions(NO_OF_PARTITIONS));
        for (int i = 0; i < TOTAL_MESSAGES; i++) {
            consumer.addRecord(new ConsumerRecord<>("foo", i % NO_OF_PARTITIONS, i, "foo-" + i, String.valueOf(i).getBytes()));
        }
        return consumer;
    }

    private Collection<TopicPartition> partitions(int noOfPartitions) {
        return IntStream.range(0, noOfPartitions)
                        .mapToObj(x -> new TopicPartition("foo", x))
                        .collect(Collectors.toList());
    }

    private void updateBeginningOffsets(String topic, int noOfPartitions, MockConsumer<String, byte[]> consumer) {
        consumer.updateBeginningOffsets(new HashMap<TopicPartition, Long>() {{
            for (int i = 0; i < noOfPartitions; i++) {
                put(new TopicPartition(topic, i), 0L);
            }
        }});
    }

    private KafkaTrackingToken emptyToken() {
        return KafkaTrackingToken.newInstance(Collections.emptyMap());
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }
}