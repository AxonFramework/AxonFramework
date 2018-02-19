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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.axonframework.kafka.eventhandling.producer.ConfirmationMode.TRANSACTIONAL;
import static org.axonframework.kafka.eventhandling.producer.ConfirmationMode.WAIT_FOR_ACK;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaPublisherTests {
    private static final String SOME_TOPIC = "topicFoo";

    @Rule
    public KafkaEmbedded embeddedKafka = new KafkaEmbedded(3, true, SOME_TOPIC);

    private KafkaPublisher<String, byte[]> testSubject;
    private SimpleEventBus eventBus;
    private DefaultProducerFactory<String, byte[]> producerFactory;
    private DefaultKafkaMessageConverter messageConverter;
    private DefaultConsumerFactory<String, byte[]> consumerFactory;

    @Before
    public void setUp() {
        this.producerFactory = DefaultProducerFactory.<String, byte[]>builder()
                .withConfigs(senderConfigs(embeddedKafka.getBrokersAsString()))
                .withConfirmationMode(WAIT_FOR_ACK)
                .build();
        this.eventBus = new SimpleEventBus();
        messageConverter = new DefaultKafkaMessageConverter(new XStreamSerializer());
        this.testSubject = new KafkaPublisher<>(publisherConfig(messageConverter));
        this.consumerFactory = new DefaultConsumerFactory<>(receiverConfigs(embeddedKafka.getBrokersAsString(), "foo", "false"));
        this.testSubject.start();
    }

    private KafkaPublisherConfiguration<String, byte[]> publisherConfig(DefaultKafkaMessageConverter messageConverter) {
        MessageMonitor<? super EventMessage<?>> messageMonitor = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessagesIngested(anyList())).thenReturn(Collections.singletonMap(mock(DomainEventMessage.class), monitorCallback));

        return KafkaPublisherConfiguration.<String, byte[]>builder()
                .withProducerFactory(producerFactory)
                .withMessageMonitor(messageMonitor)
                .withMessageSource(eventBus)
                .withMessageConverter(messageConverter)
                .withTopic(SOME_TOPIC)
                .build();
    }

    @Test
    public void testSendMessage_NoUnitOfWork() {
        List<GenericDomainEventMessage<String>> messages = domainMessages("62457", 5);
        eventBus.publish(messages);
        Consumer<String, byte[]> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singleton(SOME_TOPIC));
        assertThat(messages.size(), CoreMatchers.is(KafkaTestUtils.getRecords(consumer).count()));
    }


    @Test
    public void testSendMessage_WithTransactionalUnitOfWork() {
        GenericDomainEventMessage<String> message = domainMessage("121");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();
        Consumer<String, byte[]> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singleton(SOME_TOPIC));

        assertThat(1, CoreMatchers.is(KafkaTestUtils.getRecords(consumer).count()));
    }

    @Test
    public void testSendMessage_WithUnitOfWorkRollback() throws Exception {
        GenericDomainEventMessage<String> message = domainMessage("123456");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.onPrepareCommit(u -> {throw new RuntimeException();});
        try {
            uow.commit();
            fail("expected exception");
        } catch (Exception e) {
            //expected
        }
        Consumer<String, byte[]> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singleton(SOME_TOPIC));
        assertTrue("Didn't expect any consumer records", KafkaTestUtils.getRecords(consumer, 100).isEmpty());
    }

//    @Test
//    public void testSendMessageWithPublisherAck_UnitOfWorkCommitted()
//            throws InterruptedException, IOException, TimeoutException {
//        testSubject.setTransactional(false);
//        testSubject.setWaitForPublisherAck(true);
//        testSubject.setPublisherAckTimeout(123);
//
//        Connection connection = mock(Connection.class);
//        when(producerFactory.createConnection()).thenReturn(connection);
//        Channel channel = mock(Channel.class);
//
//        when(channel.isOpen()).thenReturn(true);
//        when(channel.waitForConfirms()).thenReturn(true);
//        when(connection.createChannel(false)).thenReturn(channel);
//        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
//        when(serializer.serialize(message.getPayload(), byte[].class))
//                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
//        when(serializer.serialize(message.getMetaData(), byte[].class))
//                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
//
//        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
//
//        eventBus.publish(message);
//        verify(channel, never()).waitForConfirms();
//
//        uow.commit();
//
//        verify(channel).confirmSelect();
//        verify(channel).basicPublish(eq("mockExchange"), eq("java.lang"),
//                                     eq(false), eq(false),
//                                     any(AMQP.BasicProperties.class), isA(byte[].class));
//        verify(channel).waitForConfirmsOrDie(123);
//        verify(channel, never()).txSelect();
//        verify(channel, never()).txCommit();
//        verify(channel, never()).txRollback();
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testCannotSetPublisherAcksAfterTransactionalSetting() {
//        testSubject.setTransactional(true);
//        testSubject.setWaitForPublisherAck(true);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testCannotSetTransactionalBehaviorAfterPublisherAcks() {
//        testSubject.setTransactional(false);
//
//        testSubject.setWaitForPublisherAck(true);
//        testSubject.setTransactional(true);
//    }
//
//    @Test
//    public void testSendMessageWithPublisherAck_NoActiveUnitOfWork() throws InterruptedException, IOException {
//        testSubject.setTransactional(false);
//        testSubject.setWaitForPublisherAck(true);
//
//        Connection connection = mock(Connection.class);
//        when(producerFactory.createConnection()).thenReturn(connection);
//        Channel channel = mock(Channel.class);
//
//        when(channel.waitForConfirms()).thenReturn(true);
//        when(connection.createChannel(false)).thenReturn(channel);
//        GenericEventMessage<String> message = new GenericEventMessage<>("Message");
//        when(serializer.serialize(message.getPayload(), byte[].class))
//                .thenReturn(new SimpleSerializedObject<>("Message".getBytes(UTF_8), byte[].class, "String", "0"));
//        when(serializer.serialize(message.getMetaData(), byte[].class))
//                .thenReturn(new SerializedMetaData<>(new byte[0], byte[].class));
//
//        eventBus.publish(message);
//        verify(channel).confirmSelect();
//        verify(channel).basicPublish(eq("mockExchange"), eq("java.lang"),
//                                     eq(false), eq(false),
//                                     any(AMQP.BasicProperties.class), isA(byte[].class));
//        verify(channel).waitForConfirmsOrDie();
//    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        testSubject.shutDown();
        producerFactory.shutDown();
        embeddedKafka.destroy();
    }

    private static Map<String, Object> receiverConfigs(String brokers, String group, String autoCommit) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "10");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 60000);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_uncommitted");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    private static Map<String, Object> senderConfigs(String brokers) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        properties.put(ProducerConfig.RETRIES_CONFIG, 0);
        return properties;
    }

    private List<GenericDomainEventMessage<String>> domainMessages(String aggregateId, int limit) {
        return IntStream.range(0, limit)
                .mapToObj(i -> domainMessage(aggregateId))
                .collect(Collectors.toList());
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, "Payload", MetaData.with("key", "value"));
    }
}
