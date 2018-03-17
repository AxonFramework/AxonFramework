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

/***
 * Tests for {@link ConsumerUtil}
 *
 * @author Nakul Mishra
 */
public class ConsumerUtilTests {

    private static final String TOPIC_1 = "testSeekConsumerWithNullToken";
    private static final String TOPIC_2 = "testSeekConsumerWithAnEmptyToken";
    private static final String TOPIC_3 = "testSeekConsumerWithAnExistingToken";

    @ClassRule
    public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(3, true, 5, TOPIC_1, TOPIC_2, TOPIC_3);

    @Test
    public void testSeekConsumerWithNullToken() {
        Consumer<String, byte[]> consumer = consumer(TOPIC_1);
        ConsumerUtil.seek(TOPIC_1, consumer, null);
        assertOffsets(TOPIC_1, consumer, 0L);
    }

    @Test
    public void testSeekConsumerWithAnEmptyToken() {
        Consumer<String, byte[]> consumer = consumer(TOPIC_2);
        ConsumerUtil.seek(TOPIC_2, consumer, KafkaTrackingToken.newInstance(Collections.emptyMap()));
        assertOffsets(TOPIC_2, consumer, 0L);
    }

    @Test
    public void testSeekConsumerWithAnExistingToken() {
        Consumer<String, byte[]> consumer = consumer(TOPIC_3);
        KafkaTrackingToken token = tokensPerTopic();
        ConsumerUtil.seek(TOPIC_3, consumer, token);

        consumer.partitionsFor(TOPIC_3).forEach(x -> assertThat(consumer.position(KafkaTrackingToken.partition(
                TOPIC_3,
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

    private void assertOffsets(String topic, Consumer<String, byte[]> consumer, long expectedPosition) {
        consumer.partitionsFor(topic)
                .forEach(x -> {
                    long currentPosition = consumer.position(KafkaTrackingToken.partition(topic, x.partition()));
                    assertThat(currentPosition, CoreMatchers.is(expectedPosition));
                });
    }

    private Consumer<String, byte[]> consumer(String groupName) {
        return new DefaultConsumerFactory<String, byte[]>(senderConfigs(embeddedKafka.getBrokersAsString(), groupName))
                .createConsumer();
    }

    private static Map<String, Object> senderConfigs(String brokers, String groupName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupName);
        return properties;
    }
}