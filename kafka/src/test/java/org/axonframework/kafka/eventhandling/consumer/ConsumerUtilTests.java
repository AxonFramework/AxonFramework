package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.springframework.kafka.test.rule.KafkaEmbedded;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConsumerUtilTests {

    private static final String SOME_TOPIC = "consumerSeekerTest";

    @Rule
    public KafkaEmbedded embeddedKafka = new KafkaEmbedded(3, true,5, SOME_TOPIC);

    @Test
    public void testSeekConsumerWithNullToken() {
        Consumer<String, byte[]> consumer = consumer();
        seek(consumer, null);
        assertOffsets(consumer, 0L);
    }

    @Test
    public void testSeekConsumerWithAnEmptyToken() {
        Consumer<String, byte[]> consumer = consumer();
        seek(consumer, KafkaTrackingToken.newInstance(Collections.emptyMap()));
        assertOffsets(consumer, 0L);
    }

    @Test
    public void testSeekConsumerWithAnExistingToken() {
        Consumer<String, byte[]> consumer = consumer();
        KafkaTrackingToken token = tokensPerTopic();
        seek(consumer, token);

        consumer.partitionsFor(SOME_TOPIC).forEach(x -> assertThat(consumer.position(KafkaTrackingToken.partition(
                SOME_TOPIC,
                x.partition())), CoreMatchers.is(token.getPartitionPositions().get(x.partition()) + 1)));
    }

    private KafkaTrackingToken tokensPerTopic() {
        int partitionsPerTopic = embeddedKafka.getPartitionsPerTopic();
        KafkaTrackingToken token = KafkaTrackingToken.newInstance(new HashMap<>());
        for (int i = 0; i < partitionsPerTopic; i++) {
            token = token.advancedTo(i, i);
        }
        return token;
    }

    private void seek(Consumer<String, byte[]> consumer, KafkaTrackingToken token) {
        ConsumerUtil.seek(SOME_TOPIC, consumer, token);
        consumer.poll(1L);
    }

    private void assertOffsets(Consumer<String, byte[]> consumer, long expectedPosition) {
        consumer.partitionsFor(SOME_TOPIC)
                .forEach(x -> {
                    long currentPosition = consumer.position(KafkaTrackingToken.partition(SOME_TOPIC, x.partition()));
                    assertThat(currentPosition, CoreMatchers.is(expectedPosition));
                });
    }

    private Consumer<String, byte[]> consumer() {
        return new DefaultConsumerFactory<String, byte[]>(senderConfigs(embeddedKafka
                                                                                .getBrokersAsString()))
                .createConsumer();
    }

    private static Map<String, Object> senderConfigs(String brokers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "consumerSeek");
        return properties;
    }
}