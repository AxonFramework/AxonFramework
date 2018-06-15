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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.transactionalConsumerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.ackProducerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.txnProducerFactory;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaPublisher}.
 *
 * @author Nakul Mishra
 */

@EmbeddedKafka(topics = {"testPublishMessagesWithAckMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully",
        "testPublishMessagesWithTransactionalMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully",
        "testSendMessagesAck_UnitOfWork",
        "testSendMessage_UnitOfWork",
        "testShouldNotPublishEventsWhenKafkaTransactionCannotBeStarted",
        "testShouldNotPublishEventsWhenKafkaTransactionCannotBeCommitted",
        "testSendMessage_WithKafkaTransactionRollback"
}, count = 3)
@RunWith(SpringRunner.class)
@DirtiesContext
public class KafkaPublisherTests {

    @Autowired
    private KafkaEmbedded kafka;
    private SimpleEventBus eventBus;
    private MessageCollector monitor;

    @Before
    public void setUp() {
        this.eventBus = new SimpleEventBus();
        this.monitor = new MessageCollector();
    }

    @Test
    public void testPublishMessagesWithAckMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully() {
        String topic = "testPublishMessagesWithAckMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully";
        ProducerFactory<String, byte[]> pf = ackProducerFactory(kafka, ByteArraySerializer.class);
        Consumer<?, ?> consumer = consumer(topic);
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        List<GenericDomainEventMessage<String>> messages = domainMessages("1234", 10);

        eventBus.publish(messages);

        assertThat(KafkaTestUtils.getRecords(consumer).count()).isEqualTo(messages.size());
        assertThat(messages).isEqualTo(monitor.received);
        assertThat(monitor.failureCounter.get()).isZero();
        assertThat(monitor.ignoreCount()).isZero();
        assertThat(monitor.successCount()).isEqualTo(messages.size());

        cleanup(pf, testSubject, consumer);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPublishingMessageAckMode_NoUnitOfWork_ButKafkaReturnTimeoutOnWrite_ShouldBeMarkedAsFailed() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.WAIT_FOR_ACK);

        Producer producer = mock(Producer.class);
        when(pf.createProducer()).thenReturn(producer);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<RecordMetadata> timeoutFuture1 = executorService.submit(() -> {
            //infinite loop for causing timeout exception
            //noinspection InfiniteLoopStatement,StatementWithEmptyBody
            for (; ; ) {
            }
        });
        Future<RecordMetadata> timeoutFuture2 = executorService.submit(() -> {
            //infinite loop for causing timeout exception
            //noinspection InfiniteLoopStatement,StatementWithEmptyBody
            for (; ; ) {
            }
        });
        when(producer.send(any())).thenReturn(timeoutFuture1).thenReturn(timeoutFuture2);
        String topic = "testSendMessagesAck_NoUnitOfWorkWithTimeout";
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        List<GenericDomainEventMessage<String>> messages = domainMessages("98765", 2);

        eventBus.publish(messages);

        assertThat(messages).isEqualTo(monitor.received);
        assertThat(monitor.successCount()).isZero();
        assertThat(monitor.failureCount()).isEqualTo(messages.size());
        assertThat(monitor.ignoreCount()).isZero();

        testSubject.shutDown();
        executorService.shutdownNow();
    }

    @Test
    public void testPublishMessagesWithTransactionalMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully() {
        Assume.assumeFalse("Transactional producers not supported on Windows",
                           System.getProperty("os.name").contains("Windows"));

        String topic = "testPublishMessagesWithTransactionalMode_NoUnitOfWork_ShouldBePublishedAndReadSuccessfully";
        ProducerFactory<String, byte[]> pf = txnProducerFactory(kafka, "foo", ByteArraySerializer.class);
        Consumer<?, ?> consumer = consumer(topic);
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        List<GenericDomainEventMessage<String>> messages = domainMessages("62457", 5);

        eventBus.publish(messages);

        assertThat(monitor.successCount()).isEqualTo(messages.size());
        assertThat(monitor.failureCount()).isZero();
        assertThat(monitor.ignoreCount()).isZero();
        assertThat(KafkaTestUtils.getRecords(consumer).count()).isEqualTo(messages.size());

        cleanup(pf, testSubject, consumer);
    }

    @Test
    public void testPublishMessagesWithAckMode_UnitOfWork_ShouldBePublishedAndReadSuccessfully() {
        String topic = "testPublishMessagesWithAckMode_UnitOfWork_ShouldBePublishedAndReadSuccessfully";
        ProducerFactory<String, byte[]> pf = ackProducerFactory(kafka, ByteArraySerializer.class);
        Consumer<?, ?> consumer = consumer(topic);
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("1234");

        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();

        assertThat(singletonList(message)).isEqualTo(monitor.received);
        assertThat(monitor.successCount()).isOne();
        assertThat(monitor.failureCount()).isZero();
        assertThat(monitor.ignoreCounter.get()).isZero();
        assertThat(KafkaTestUtils.getRecords(consumer).count()).isOne();

        cleanup(pf, testSubject, consumer);
    }

    @Test
    public void testPublishMessagesWithTransactionalMode_UnitOfWork_ShouldBePublishedAndReadSuccessfully() {
        Assume.assumeFalse("Transactional producers not supported on Windows",
                           System.getProperty("os.name").contains("Windows"));

        String topic = "testPublishMessagesWithTransactionalMode_UnitOfWork_ShouldBePublishedAndReadSuccessfully";
        ProducerFactory<String, byte[]> pf = txnProducerFactory(kafka, "foo", ByteArraySerializer.class);
        Consumer<?, ?> consumer = consumer(topic);
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("121");

        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();

        assertThat(KafkaTestUtils.getRecords(consumer).count()).isOne();

        cleanup(pf, testSubject, consumer);
    }

    @Test
    public void testPublishMessageWithTransactionalMode_UnitOfWorkRollback_ShouldNeverBePublished() {
        String topic = "testPublishMessageWithTransactionalMode_UnitOfWorkRollback_ShouldNeverBePublished";
        ProducerFactory<String, byte[]> pf = txnProducerFactory(kafka, "foo", ByteArraySerializer.class);
        Consumer<?, ?> consumer = consumer(topic);
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("123456");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.onPrepareCommit(u -> {
            throw new RuntimeException();
        });
        try {
            uow.commit();
            fail("expected exception");
        } catch (Exception e) {
            //expected
        }

        assertTrue("Didn't expect any consumer records", KafkaTestUtils.getRecords(consumer, 100).isEmpty());

        cleanup(pf, testSubject, consumer);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventPublicationFailedException.class)
    public void testPublishMessages_KafkaTransactionCannotBeStarted_ShouldThrowAnException() {
        String topic = "testPublishMessages_KafkaTransactionCannotBeStarted_ShouldThrowAnException";
        DefaultProducerFactory pf = producerFactoryWithFencedExceptionOnBeginTransaction();
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("500");

        publishWithException(topic, testSubject, message);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventPublicationFailedException.class)
    public void testPublishMessage_KafkaTransactionCannotBeCommitted_ShouldThrowAnException() {
        String topic = "testPublishMessage_KafkaTransactionCannotBeCommitted_ShouldNotPublishEvents";
        DefaultProducerFactory pf = producerFactoryWithFencedExceptionOnCommit();
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("9000");

        publishWithException(topic, testSubject, message);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendMessage_WithKafkaTransactionRollback() {
        DefaultProducerFactory pf = producerFactoryWithFencedExceptionOnAbort();
        String topic = "testSendMessage_WithKafkaTransactionRollback";
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("76123");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.onPrepareCommit(u -> {
            throw new RuntimeException();
        });
        try {
            uow.commit();
            fail("expected exception");
        } catch (Exception e) {
            //expected
        }

        Consumer<?, ?> consumer = consumer(topic);
        assertTrue("Didn't expect any consumer records", KafkaTestUtils.getRecords(consumer, 100).isEmpty());
        consumer.close();
        testSubject.shutDown();
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        this.monitor.reset();
    }

    private static DefaultProducerFactory producerFactoryWithFencedExceptionOnAbort() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(RuntimeException.class).when(producer).abortTransaction();
        return pf;
    }

    private static DefaultProducerFactory producerFactoryWithFencedExceptionOnBeginTransaction() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(ProducerFencedException.class).when(producer).beginTransaction();
        return pf;
    }

    private static DefaultProducerFactory producerFactoryWithFencedExceptionOnCommit() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(ProducerFencedException.class).when(producer).commitTransaction();
        return pf;
    }

    private KafkaPublisher<?, ?> publisher(String topic, ProducerFactory<String, byte[]> pf) {
        KafkaPublisher<?, ?> testSubject = new KafkaPublisher<>(
                KafkaPublisherConfiguration.<String, byte[]>builder()
                        .withProducerFactory(pf)
                        .withPublisherAckTimeout(1000)
                        .withMessageMonitor(monitor)
                        .withMessageSource(eventBus)
                        .withMessageConverter(new DefaultKafkaMessageConverter(new XStreamSerializer()))
                        .withTopic(topic)
                        .build()
        );
        testSubject.start();
        return testSubject;
    }

    private Consumer<?, ?> consumer(String topic) {
        Consumer<?, ?> consumer = transactionalConsumerFactory(
                kafka, topic, ByteArrayDeserializer.class
        ).createConsumer();
        consumer.subscribe(Collections.singleton(topic));
        return consumer;
    }

    private static void cleanup(ProducerFactory<String, byte[]> pf, KafkaPublisher<?, ?> testSubject,
                                Consumer<?, ?> consumer) {
        consumer.close();
        pf.shutDown();
        testSubject.shutDown();
    }

    private static List<GenericDomainEventMessage<String>> domainMessages(String aggregateId, int limit) {
        return IntStream.range(0, limit)
                        .mapToObj(i -> domainMessage(aggregateId))
                        .collect(Collectors.toList());
    }

    private static GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }

    private void publishWithException(String topic, KafkaPublisher<?, ?> testSubject,
                                      GenericDomainEventMessage<String> message) {
        try {
            UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
            eventBus.publish(message);
            uow.commit();
        } finally {
            Consumer<?, ?> consumer = consumer(topic);
            assertTrue("Didn't expect any consumer records", KafkaTestUtils.getRecords(consumer, 100).isEmpty());
            consumer.close();
            testSubject.shutDown();
        }
    }

    private static class MessageCollector implements MessageMonitor<Message<?>> {

        private final AtomicInteger successCounter = new AtomicInteger(0);
        private final AtomicInteger failureCounter = new AtomicInteger(0);
        private final AtomicInteger ignoreCounter = new AtomicInteger(0);

        private List<Message<?>> received = new CopyOnWriteArrayList<>();

        @Override
        public MonitorCallback onMessageIngested(Message<?> message) {
            received.add(message);
            return new MonitorCallback() {
                @Override
                public void reportSuccess() {
                    successCounter.incrementAndGet();
                }

                @Override
                public void reportFailure(Throwable cause) {
                    failureCounter.incrementAndGet();
                }

                @Override
                public void reportIgnored() {
                    ignoreCounter.incrementAndGet();
                }
            };
        }

        int successCount() {
            return successCounter.get();
        }

        int failureCount() {
            return failureCounter.get();
        }

        int ignoreCount() {
            return ignoreCounter.get();
        }

        void reset() {
            received.clear();
        }
    }
}
