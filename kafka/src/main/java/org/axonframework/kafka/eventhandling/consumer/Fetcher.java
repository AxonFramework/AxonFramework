package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.MessageStream;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Nakul Mishra
 */
public class Fetcher<K, V> {
    private final BlockingQueue<MessageAndTimestamp> buffer;
    private final ExecutorService pool;
    private final KafkaMessageConverter<K, V> converter;
    private final Consumer<K, V> consumer;
    private final String topic;
    private Future<?> currentTask;

    public Fetcher(int bufferSize,
            ConsumerFactory<K, V> consumerFactory,
            KafkaMessageConverter<K, V> converter, String topic) {

        this.buffer = new PriorityBlockingQueue<>(bufferSize);
        this.converter = converter;
        pool = Executors.newSingleThreadExecutor();
        this.topic = topic;
        this.consumer = consumerFactory.createConsumer();
    }

    public MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token) {
        ConsumerUtil.seek(topic, consumer, token);
        if (KafkaTrackingToken.isEmpty(token)) {
            token = KafkaTrackingToken.newInstance(new HashMap<>());
        }
        currentTask = pool.submit(new FetchEventsTask<>(consumer, token, buffer, converter));
        return new KafkaMessageStream<>(buffer, this);
    }

    public void shutdown() {
        currentTask.cancel(true);
        pool.shutdown();
        consumer.close();
    }

}