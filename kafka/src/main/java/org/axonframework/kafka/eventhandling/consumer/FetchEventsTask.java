package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 *
 * @author Nakul Mishra
 */
class FetchEventsTask<K, V> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(FetchEventsTask.class);
    private static final long DEFAULT_TIMEOUT = 3000;

    private final Consumer<K, V> consumer;
    private final BlockingQueue<MessageAndTimestamp> channel;
    private final KafkaMessageConverter<K, V> converter;
    private KafkaTrackingToken start;

    FetchEventsTask(Consumer<K, V> consumer,
                    KafkaTrackingToken start, BlockingQueue<MessageAndTimestamp> channel,
                    KafkaMessageConverter<K, V> converter) {
        Assert.isTrue(consumer != null, () -> "Consumer may not be null");
        Assert.isTrue(channel != null, () -> "Buffer may not be null");
        Assert.isTrue(converter != null, () -> "Converter may not be null");
        this.consumer = consumer;
        this.start = start;
        this.channel = channel;
        this.converter = converter;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            ConsumerRecords<K, V> records = consumer.poll(DEFAULT_TIMEOUT);
            logger.debug("Records fetched: {}", records.count());
            for (ConsumerRecord<K, V> record : records) {
                converter.readKafkaMessage(record).ifPresent(m -> {
                    try {
                        start = start.advancedTo(record.partition(), record.offset());
                        TrackedEventMessage<?> msg = asTrackedEventMessage(m, start);
                        channel.put(new MessageAndTimestamp(msg, record.timestamp()));
                    } catch (InterruptedException e) {
                        logger.warn("Event producer thread was interrupted. Shutting down.", e);
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }
}
