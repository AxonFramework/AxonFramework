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
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Publisher implementation that uses Kafka Message Broker to dispatch event messages. All outgoing messages are sent to
 * a configured topics.
 * <p>
 * This terminal does not dispatch Events internally, as it relies on each event processor to listen to it's own Kafka
 * Topic.
 *
 * @param <K> a generic type for the key of the {@link ProducerFactory}, {@link Producer} and
 *            {@link KafkaMessageConverter}
 * @param <V> a generic type for the value of the {@link ProducerFactory}, {@link Producer} and
 *            {@link KafkaMessageConverter}
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
     * Instantiate a {@link KafkaPublisher} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link SubscribableMessageSource} and {@link ProducerFactory} are not {@code null}, and will
     * throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link KafkaPublisher} instance
     */
    protected KafkaPublisher(Builder<K, V> builder) {
        builder.validate();
        this.messageSource = builder.messageSource;
        this.producerFactory = builder.producerFactory;
        this.messageConverter = builder.messageConverter;
        this.messageMonitor = builder.messageMonitor;
        this.topic = builder.topic;
        this.publisherAckTimeout = builder.publisherAckTimeout;
    }

    /**
     * Instantiate a Builder to be able to create a {@link KafkaPublisher}.
     * <p>
     * The {@link KafkaMessageConverter} is defaulted to a {@link DefaultKafkaMessageConverter}, the
     * {@link MessageMonitor} to a {@link NoOpMessageMonitor}, the {@code topic} to {code Axon.Events} and the
     * {@code publisherAckTimeout} to {@code 1000} milliseconds. The {@link SubscribableMessageSource} and
     * {@link ProducerFactory} are <b>hard requirements</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link ProducerFactory}, {@link Producer} and
     *            {@link KafkaMessageConverter}
     * @param <V> a generic type for the value of the {@link ProducerFactory}, {@link Producer} and
     *            {@link KafkaMessageConverter}
     * @return a Builder to be able to create a {@link KafkaPublisher}
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
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
        }
        producerFactory.shutDown();
    }

    /**
     * Send {@code events} to the configured Kafka {@code topic}. It takes the current Unit of Work into account when
     * available.
     * <p>
     * If {@link ProducerFactory} is configured to use:
     * <ul>
     * <li>Transactions: use kafka transactions for publishing events</li>
     * <li>Ack: send messages and wait for acknowledgement from Kafka. Acknowledgement timeout can be configured via
     * {@link KafkaPublisher.Builder#publisherAckTimeout(long)}).</li>
     * <li>None: fire and forget.</li>
     * </ul>
     *
     * @param events the events to publish on the Kafka broker.
     */
    protected void send(List<? extends EventMessage<?>> events) {
        final Map<? super EventMessage<?>, MonitorCallback> monitorCallbacks = messageMonitor
                .onMessagesIngested(events);
        Producer<K, V> producer = producerFactory.createProducer();
        ConfirmationMode cm = producerFactory.confirmationMode();
        try {
            if (cm.isTransactional()) {
                tryBeginTxn(producer);
            }
            Map<Future<RecordMetadata>, ? super EventMessage<?>> publishStatuses = publishToKafka(events, producer);
            if (CurrentUnitOfWork.isStarted()) {
                handleActiveUnitOfWork(producer, publishStatuses, monitorCallbacks, cm);
            } else if (cm.isTransactional()) {
                tryCommit(producer, monitorCallbacks);
            } else if (cm.isWaitForAck()) {
                waitForPublishAck(publishStatuses, monitorCallbacks);
            }
        } finally {
            if (!CurrentUnitOfWork.isStarted()) {
                tryClose(producer);
            }
        }
    }

    /**
     * Send's event messages to Kafka.
     *
     * @param events   list of event messages to publish.
     * @param producer Kafka producer used for publishing.
     * @return Map containing futures for each event that was published to kafka. You can interact with a specific
     * {@link Future} to check whether a giSpringAxonAutoConfigurerTestven message was published successfully or not.
     */
    private Map<Future<RecordMetadata>, ? super EventMessage<?>> publishToKafka(List<? extends EventMessage<?>> events,
                                                                                Producer<K, V> producer) {
        Map<Future<RecordMetadata>, ? super EventMessage<?>> results = new HashMap<>();
        events.forEach(event -> results.put(producer.send(messageConverter.createKafkaMessage(event, topic)), event));
        return results;
    }

    /**
     * Commit/rollback Kafka work once a given unit of work is committed/rollback.
     */
    private void handleActiveUnitOfWork(Producer<K, V> producer,
                                        Map<Future<RecordMetadata>, ? super EventMessage<?>> futures,
                                        Map<? super EventMessage<?>, MonitorCallback> monitorCallbacks,
                                        ConfirmationMode confirmationMode) {
        UnitOfWork<?> uow = CurrentUnitOfWork.get();
        uow.afterCommit(u -> completeKafkaWork(monitorCallbacks, producer, confirmationMode, futures));
        uow.onRollback(u -> rollbackKafkaWork(producer, confirmationMode));
    }

    private void completeKafkaWork(Map<? super EventMessage<?>, MonitorCallback> monitorCallbackMap,
                                   Producer<K, V> producer, ConfirmationMode confirmationMode,
                                   Map<Future<RecordMetadata>, ? super EventMessage<?>> futures) {
        if (confirmationMode.isTransactional()) {
            tryCommit(producer, monitorCallbackMap);
        } else if (confirmationMode.isWaitForAck()) {
            waitForPublishAck(futures, monitorCallbackMap);
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
    private void waitForPublishAck(Map<Future<RecordMetadata>, ? super EventMessage<?>> futures,
                                   Map<? super EventMessage<?>, MonitorCallback> monitorCallbacks) {
        long deadline = System.currentTimeMillis() + publisherAckTimeout;
        futures.forEach((k, v) -> {
            try {
                k.get(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (monitorCallbacks.containsKey(v)) {
                    monitorCallbacks.get(v).reportSuccess();
                }
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                monitorCallbacks.get(v).reportFailure(ex);
                logger.warn("Encountered error while waiting for event publication", ex);
            }
        });
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

    private void tryCommit(Producer<?, ?> producer,
                           Map<? super EventMessage<?>, MonitorCallback> monitorCallbacks) {
        try {
            producer.commitTransaction();
            monitorCallbacks.forEach((k, v) -> v.reportSuccess());
        } catch (ProducerFencedException e) {
            logger.warn("Unable to commit transaction", e);
            monitorCallbacks.forEach((k, v) -> v.reportFailure(e));
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

    /**
     * Builder class to instantiate a {@link KafkaPublisher}.
     * <p>
     * The {@link KafkaMessageConverter} is defaulted to a {@link DefaultKafkaMessageConverter}, the
     * {@link MessageMonitor} to a {@link NoOpMessageMonitor}, the {@code topic} to {code Axon.Events} and the
     * {@code publisherAckTimeout} to {@code 1000} milliseconds. The {@link SubscribableMessageSource} and
     * {@link ProducerFactory} are a <b>hard requirements</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link ProducerFactory}, {@link Producer} and
     *            {@link KafkaMessageConverter}
     * @param <V> a generic type for the value of the {@link ProducerFactory}, {@link Producer} and
     *            {@link KafkaMessageConverter}
     */
    public static class Builder<K, V> {

        private SubscribableMessageSource<EventMessage<?>> messageSource;
        private ProducerFactory<K, V> producerFactory;
        @SuppressWarnings("unchecked")
        private KafkaMessageConverter<K, V> messageConverter =
                (KafkaMessageConverter<K, V>) DefaultKafkaMessageConverter.builder()
                                                                          .serializer(new XStreamSerializer())
                                                                          .build();
        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.instance();
        private String topic = "Axon.Events";
        private long publisherAckTimeout = 1_000;

        /**
         * Sets the {@link SubscribableMessageSource} of type {@link EventMessage} which will provide the EventMessages
         * to be published on the Kafka topic.
         *
         * @param messageSource a {@link SubscribableMessageSource} of type {@link EventMessage} which will provide the
         *                      EventMessages to be published on the Kafka topic
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> messageSource(SubscribableMessageSource<EventMessage<?>> messageSource) {
            assertNonNull(messageSource, "SubscribableMessageSource may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Sets the {@link ProducerFactory} which will instantiate {@link Producer} instances to publish
         * {@link EventMessage}s on the Kafka topic.
         *
         * @param producerFactory a {@link ProducerFactory} which will instantiate {@link Producer} instances to publish
         *                        {@link EventMessage}s on the Kafka topic
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> producerFactory(ProducerFactory<K, V> producerFactory) {
            assertNonNull(producerFactory, "ProducerFactory may not be null");
            this.producerFactory = producerFactory;
            return this;
        }

        /**
         * Sets the {@link KafkaMessageConverter} used to convert {@link EventMessage}s into Kafka messages. Defaults to
         * a {@link DefaultKafkaMessageConverter} using the {@link XStreamSerializer}.
         * <p>
         * Note that configuring a MessageConverter on the builder is mandatory if the value type is not {@code byte[]}.
         *
         * @param messageConverter a {@link KafkaMessageConverter} used to convert {@link EventMessage}s into Kafka
         *                         messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> messageConverter(KafkaMessageConverter<K, V> messageConverter) {
            assertNonNull(messageConverter, "MessageConverter may not be null");
            this.messageConverter = messageConverter;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} of generic type {@link EventMessage} used the to monitor this Kafka
         * publisher. Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} used to monitor this Kafka publisher
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Set the Kafka {@code topic} to publish {@link EventMessage}s on. Defaults to {@code Axon.Events}.
         *
         * @param topic the Kafka {@code topic} to publish {@link EventMessage}s on
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> topic(String topic) {
            assertThat(topic, name -> Objects.nonNull(name) && !"".equals(name), "The topic may not be null or empty");
            this.topic = topic;
            return this;
        }

        /**
         * Sets the publisher acknowledge timeout in milliseconds specifying how long to wait for a publisher to
         * acknowledge a message has been sent. Defaults to {@code 1000} milliseconds.
         *
         * @param publisherAckTimeout a {@code long} specifying how long to wait for a publisher to acknowledge a
         *                            message has been sent
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> publisherAckTimeout(long publisherAckTimeout) {
            assertThat(publisherAckTimeout,
                       timeout -> timeout >= 0,
                       "The publisherAckTimeout should be a positive number or zero");
            this.publisherAckTimeout = publisherAckTimeout;
            return this;
        }

        /**
         * Initializes a {@link KafkaPublisher} as specified through this Builder.
         *
         * @return a {@link KafkaPublisher} as specified through this Builder
         */
        public KafkaPublisher<K, V> build() {
            return new KafkaPublisher<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(messageSource, "The SubscribableMessageSource is a hard requirement and should be provided");
            assertNonNull(producerFactory, "The ProducerFactory is a hard requirement and should be provided");
        }
    }
}