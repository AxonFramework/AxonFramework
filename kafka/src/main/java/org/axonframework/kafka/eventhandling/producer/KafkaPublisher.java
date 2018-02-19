/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * EventBusTerminal implementation that uses Kafka Message Broker to dispatch event messages. All
 * outgoing messages are sent to a configured topics.
 * <p>
 * This terminal does not dispatch Events internally, as it relies on each event processor to listen to it's own Kafka
 * Topic.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaPublisher<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPublisher.class);

    private final SubscribableMessageSource<EventMessage<?>> messageSource;
    private final ProducerFactory<K, V> producerFactory;
    private final KafkaMessageConverter<K, V> messageConverter;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final String topic;
    private final long publisherAckTimeout;

    private Registration eventBusRegistration;

    /**
     * Initialize this instance to publish message as they are published on the given {@code messageSource}.
     *
     * @param config Publisher configuration used for initialization
     */
    public KafkaPublisher(KafkaPublisherConfiguration<K, V> config) {
        this.messageSource = config.getMessageSource();
        this.producerFactory = config.getProducerFactory();
        this.messageConverter = config.getMessageConverter();
        this.messageMonitor = config.getMessageMonitor();
        this.topic = config.getTopic();
        this.publisherAckTimeout = config.getPublisherAckTimeout();
    }

    /**
     * Subscribes this publisher to the messageSource provided during initialization.
     */
    public void start() {
        eventBusRegistration = messageSource.subscribe(this::send);
    }

    /**
     * Shuts down this component and unsubscribes it from its messageSource.
     */
    public void shutDown() {
        if (eventBusRegistration != null) {
            eventBusRegistration.cancel();
            eventBusRegistration = null;
            //TODO : Perhaps we, should we close all producers instances as well i.e. producerFactory.close() ???
        }
    }

    /**
     * Sends the given {@code events} to the configured Kafka topic. It takes the current Unit of Work into account when
     * available.
     * If producer factory is configured to use:
     * a) Transactions: it will use kafka transactions for sending events
     * b) Ack: it will send messages and wait for kafka to acknowledge message publication (timeout configured via
     * publisherAckTimeout).
     * Furthermore, it will use {@link MessageMonitor} to notify whether a given message was published successfully or
     * not.
     * c) Otherwise, it simply publishes directly.
     *
     * @param events the events to publish on the Kafka broker.
     */
    protected void send(List<? extends EventMessage<?>> events) {
        final Map<? super EventMessage<?>, MonitorCallback> monitorCallbackMap = ingestMessages(events);

        Producer<K, V> producer = producerFactory.createProducer();
        ConfirmationMode confirmationMode = producerFactory.getConfirmationMode();

        try {
            if (confirmationMode.isTransactional()) {
                tryBeginTxn(producer);
            }

            Map<Future<RecordMetadata>, ? super EventMessage<?>> kafkaFutures = publishEventsToKafka(events, producer);

            if (CurrentUnitOfWork.isStarted()) {
                handleActiveUnitOfWork(monitorCallbackMap, producer, confirmationMode, kafkaFutures);
            } else if (confirmationMode.isTransactional()) {
                tryCommit(producer);
            } else if (confirmationMode.isWaitForAck()) {
                waitForPublishAck(monitorCallbackMap, kafkaFutures);
            }
        } finally {
            if (!CurrentUnitOfWork.isStarted()) {
                tryClose(producer);
            }
        }
    }

    private Map<? super EventMessage<?>, MonitorCallback> ingestMessages(List<? extends EventMessage<?>> events) {
        return messageMonitor.onMessagesIngested(events);
    }

    /**
     * Send's event messages to kafka
     *
     * @param events   list of event messages to publish
     * @param producer Kafka producer used for publishing
     * @return Map containing futures for each event that was published to kafka. You can interact with a specific
     * future to check whether a given message was published successfully or not.
     */
    private Map<Future<RecordMetadata>, ? super EventMessage<?>> publishEventsToKafka(
            List<? extends EventMessage<?>> events, Producer<K, V> producer) {
        Map<Future<RecordMetadata>, ? super EventMessage<?>> results = new HashMap<>();
        events.forEach(event -> {
            results.put(doSendMessage(producer, messageConverter.createKafkaMessage(event, topic)), event);
        });
        return results;
    }

    /**
     * It will commit/rollback kafka work once a given unit of work is committed/rollback.
     */
    private void handleActiveUnitOfWork(Map<? super EventMessage<?>, MonitorCallback> monitorCallbackMap,
                                        Producer<K, V> producer, ConfirmationMode confirmationMode,
                                        Map<Future<RecordMetadata>, ? super EventMessage<?>> futures) {
        UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
        unitOfWork.afterCommit(u -> completeKafkaWork(monitorCallbackMap, producer, confirmationMode, futures));
        unitOfWork.onRollback(u -> rollbackKafkaWork(producer, confirmationMode));
    }

    private void completeKafkaWork(Map<? super EventMessage<?>, MonitorCallback> monitorCallbackMap,
                                   Producer<K, V> producer, ConfirmationMode confirmationMode,
                                   Map<Future<RecordMetadata>, ? super EventMessage<?>> futures) {
        if (confirmationMode.isTransactional()) {
            tryCommit(producer);
        } else if (confirmationMode.isWaitForAck()) {
            waitForPublishAck(monitorCallbackMap, futures);
        }
        tryClose(producer);
    }

    private void rollbackKafkaWork(Producer<K, V> producer, ConfirmationMode confirmationMode) {
        if (confirmationMode.isTransactional()) {
            tryRollback(producer);
        }
        tryClose(producer);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private void waitForPublishAck(Map<? super EventMessage<?>, MonitorCallback> monitorCallbackMap,
                                   Map<Future<RecordMetadata>, ? super EventMessage<?>> futures) {
        long deadline = System.currentTimeMillis() + publisherAckTimeout;
        futures.forEach((k, v) -> {
            try {
                k.get(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (monitorCallbackMap.containsKey(v)) {
                    monitorCallbackMap.get(v).reportSuccess();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                monitorCallbackMap.get(v).reportFailure(ex);
                logger.warn("Encountered error while waiting for event publication", ex);
            }
        });
    }

    /**
     * Does the actual publishing of the given {@link ProducerRecord} on the given {@code kafka topic}.
     *
     * @param producer {@link org.apache.kafka.clients.producer.KafkaProducer} to send messages
     * @param record   The {@link org.apache.kafka.clients.producer.ProducerRecord} to publish
     */
    protected Future<RecordMetadata> doSendMessage(Producer<K, V> producer, ProducerRecord<K, V> record) {
        return producer.send(record);
    }

    private void tryBeginTxn(Producer<?, ?> producer) {
        try {
            producer.beginTransaction();
        } catch (ProducerFencedException e) {
            logger.warn("Unable to begin transaction", e);
            throw new EventPublicationFailedException(
                    "Event publication failed: Exception occurred while starting kafka transaction",
                    e);
        }
    }

    private void tryCommit(Producer<?, ?> producer) {
        try {
            producer.commitTransaction();
        } catch (ProducerFencedException e) {
            logger.warn("Unable to commit transaction", e);
            throw new EventPublicationFailedException(
                    "Event publication failed: Exception occurred while committing kafka transaction",
                    e);
        }
    }

    private void tryClose(Producer<?, ?> producer) {
        try {
            producer.close();
        } catch (Exception e) {
            logger.debug("Unable to close producer.", e);
            //not re-throwing exception, can't do anything
        }
    }

    private void tryRollback(Producer<?, ?> producer) {
        try {
            producer.abortTransaction();
        } catch (Exception e) {
            logger.warn("Unable to abort transaction", e);
            //not re-throwing exception, its too late
        }
    }
}

