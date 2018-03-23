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
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.axonframework.kafka.eventhandling.ConsumerConfigUtil.transactionalConsumerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.ackProducerFactory;
import static org.axonframework.kafka.eventhandling.ProducerConfigUtil.txnProducerFactory;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaPublisher}
 *
 * @author Nakul Mishra
 */

@RunWith(SpringRunner.class)
@EmbeddedKafka(topics = {"testSendMessagesAck_NoUnitOfWork",
        "testSendMessage_NoUnitOfWork",
        "testSendMessagesAck_UnitOfWork",
        "testSendMessage_UnitOfWork",
        "testShouldNotPublishEventsWhenKafkaTransactionCannotBeStarted",
        "testShouldNotPublishEventsWhenKafkaTransactionCannotBeCommitted",
        "testSendMessage_WithKafkaTransactionRollback"}, count = 3)
public class KafkaPublisherTests {

    @Autowired
    private KafkaEmbedded kafka;
    private ProducerFactory<String, byte[]> txnProducerFactory;
    private ProducerFactory<String, byte[]> ackProducerFactory;
    private SimpleEventBus eventBus;
    private DefaultKafkaMessageConverter messageConverter;
    private MessageCollector messageMonitor;

    @Before
    public void setUp() {
        this.txnProducerFactory = txnProducerFactory(kafka, "foo", ByteArraySerializer.class);
        this.ackProducerFactory = ackProducerFactory(kafka, ByteArraySerializer.class);
        this.eventBus = new SimpleEventBus();
        this.messageConverter = new DefaultKafkaMessageConverter(new XStreamSerializer());
        this.messageMonitor = new MessageCollector();
    }

    @Test
    public void testSendMessagesAck_NoUnitOfWork() {
        String topic = "testSendMessagesAck_NoUnitOfWork";
        KafkaPublisher<?, ?> testSubject = publisher(topic, ackProducerFactory);
        List<GenericDomainEventMessage<String>> messages = domainMessages("1234", 10);
        eventBus.publish(messages);
        Consumer<?, ?> consumer = consumer(topic);
        assertMessageMonitor(messages, messages.size(), 0);
        assertThat(KafkaTestUtils.getRecords(consumer).count(), is(messages.size()));
        testSubject.shutDown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendMessagesAck_NoUnitOfWorkWithTimeout() {
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
        assertEquals(messages, messageMonitor.messages);
        assertMessageMonitor(messages, 0, messages.size());
        testSubject.shutDown();
        executorService.shutdownNow();
    }

    @Test
    public void testSendMessage_NoUnitOfWork() {
        String topic = "testSendMessage_NoUnitOfWork";
        KafkaPublisher<?, ?> testSubject = publisher(topic, txnProducerFactory);
        List<GenericDomainEventMessage<String>> messages = domainMessages("62457", 5);
        eventBus.publish(messages);
        Consumer<?, ?> consumer = consumer(topic);
        assertThat(KafkaTestUtils.getRecords(consumer).count(), is(messages.size()));
        consumer.close();
        testSubject.shutDown();
    }

    @Test
    public void testSendMessageAck_UnitOfWork() {
        String topic = "testSendMessagesAck_UnitOfWork";
        KafkaPublisher<?, ?> testSubject = publisher(topic, ackProducerFactory);
        GenericDomainEventMessage<String> message = domainMessage("1234");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();
        assertEquals(message, messageMonitor.messages.get(0));
        assertMessageMonitor(Collections.singletonList(message), 1, 0);
        Consumer<?, ?> consumer = consumer(topic);
        assertThat(KafkaTestUtils.getRecords(consumer).count(), is(1));
        testSubject.shutDown();
    }

    @Test
    public void testSendMessage_UnitOfWork() {
        String topic = "testSendMessage_WithTransactionalUnitOfWork";
        KafkaPublisher<?, ?> testSubject = publisher(topic, txnProducerFactory);
        GenericDomainEventMessage<String> message = domainMessage("121");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();
        Consumer<?, ?> consumer = consumer(topic);
        assertThat(KafkaTestUtils.getRecords(consumer).count(), is(1));
        consumer.close();
        testSubject.shutDown();
    }

    @Test
    public void testSendMessage_UnitOfWorkRollback() {
        String topic = "testSendMessage_WithUnitOfWorkRollback";
        KafkaPublisher<?, ?> testSubject = publisher(topic, txnProducerFactory);
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
        Consumer<?, ?> consumer = consumer(topic);
        assertTrue("Didn't expect any consumer records", KafkaTestUtils.getRecords(consumer, 100).isEmpty());
        consumer.close();
        testSubject.shutDown();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventPublicationFailedException.class)
    public void testShouldNotPublishEventsWhenKafkaTransactionCannotBeStarted() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(ProducerFencedException.class).when(producer).beginTransaction();
        String topic = "testShouldNotPublishEventsWhenKafkaTransactionCannotBeStarted";
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("500");
        publishWithException(topic, testSubject, message);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = EventPublicationFailedException.class)
    public void testShouldNotPublishEventsWhenKafkaTransactionCannotBeCommitted() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(ProducerFencedException.class).when(producer).commitTransaction();
        String topic = "testShouldNotPublishEventsWhenKafkaTransactionCannotBeCommitted";
        KafkaPublisher<?, ?> testSubject = publisher(topic, pf);
        GenericDomainEventMessage<String> message = domainMessage("9000");
        publishWithException(topic, testSubject, message);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendMessage_WithKafkaTransactionRollback() {
        DefaultProducerFactory pf = mock(DefaultProducerFactory.class);
        Producer producer = mock(Producer.class);
        when(pf.confirmationMode()).thenReturn(ConfirmationMode.TRANSACTIONAL);
        when(pf.createProducer()).thenReturn(producer);
        doThrow(Exception.class).when(producer).abortTransaction();
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
        this.messageMonitor.reset();
        this.ackProducerFactory.shutDown();
        this.txnProducerFactory.shutDown();
    }

    private KafkaPublisher<?, ?> publisher(String topic, ProducerFactory<String, byte[]> pf) {
        KafkaPublisher<?, ?> testSubject = new KafkaPublisher<>(KafkaPublisherConfiguration.<String, byte[]>builder()
                                                                        .withProducerFactory(pf)
                                                                        .withPublisherAckTimeout(100)
                                                                        .withMessageMonitor(messageMonitor)
                                                                        .withMessageSource(eventBus)
                                                                        .withMessageConverter(messageConverter)
                                                                        .withTopic(topic)
                                                                        .build());
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

    private List<GenericDomainEventMessage<String>> domainMessages(String aggregateId, int limit) {
        return IntStream.range(0, limit)
                        .mapToObj(i -> domainMessage(aggregateId))
                        .collect(Collectors.toList());
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }

    private void assertMessageMonitor(List<?> messages, int expectedSuccess, int expectedFailures) {
        assertEquals(messages, messageMonitor.messages);
        assertThat(messageMonitor.successCounter.get(), is(expectedSuccess));
        assertThat(messageMonitor.failureCounter.get(), is(expectedFailures));
        assertThat(messageMonitor.ignoreCounter.get(), is(0));
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

    private class MessageCollector implements MessageMonitor<Message<?>> {

        private final List<Message<?>> messages = new ArrayList<>();
        private final AtomicInteger successCounter = new AtomicInteger(0);
        private final AtomicInteger failureCounter = new AtomicInteger(0);
        private final AtomicInteger ignoreCounter = new AtomicInteger(0);

        @Override
        public MonitorCallback onMessageIngested(Message<?> message) {
            messages.add(message);
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

        void reset() {
            messages.clear();
        }
    }
}
