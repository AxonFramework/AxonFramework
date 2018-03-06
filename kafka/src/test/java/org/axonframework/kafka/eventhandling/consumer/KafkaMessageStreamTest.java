package org.axonframework.kafka.eventhandling.consumer;

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
import org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.*;
import org.springframework.kafka.test.rule.KafkaEmbedded;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.axonframework.kafka.eventhandling.producer.ConfirmationMode.WAIT_FOR_ACK;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaMessageStreamTest {
    private static final String SOME_TOPIC = "topicFoo";

    private final int NUMBER_OF_BROKERS = 3;
    private final int NUMBER_OF_PARTITIONS = 3;
    @Rule
    public KafkaEmbedded embeddedKafka = new KafkaEmbedded(NUMBER_OF_BROKERS, true, NUMBER_OF_PARTITIONS, SOME_TOPIC);

    private KafkaPublisher<String, byte[]> kafkaPublisher;
    private SimpleEventBus eventBus;
    private DefaultProducerFactory<String, byte[]> producerFactory;
    private DefaultKafkaMessageConverter messageConverter;
    private DefaultConsumerFactory<String, byte[]> consumerFactory;
/*
    @Before
    public void setUp() {
        this.producerFactory = DefaultProducerFactory.<String, byte[]>builder()
                .withConfigs(senderConfigs(embeddedKafka.getBrokersAsString()))
                .withConfirmationMode(WAIT_FOR_ACK)
                .build();
        this.eventBus = new SimpleEventBus();
        messageConverter = new DefaultKafkaMessageConverter(new XStreamSerializer());
        this.kafkaPublisher = new KafkaPublisher<>(publisherConfig(messageConverter));
        this.consumerFactory = new DefaultConsumerFactory<>(receiverConfigs(embeddedKafka.getBrokersAsString(), "foo", "false"));
        this.kafkaPublisher.start();
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
    public void peek() {
        GenericDomainEventMessage<String> message = domainMessage("121");
        UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(message);
        eventBus.publish(message);
        uow.commit();
        Consumer<String, byte[]> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singleton(SOME_TOPIC));
        KafkaMessageStream<String, byte[]> testSubject = new KafkaMessageStream<>(null, consumer, messageConverter);
        testSubject.hasNextAvailable(2000, TimeUnit.MILLISECONDS);
        assertThat(message.getIdentifier(), CoreMatchers.is(testSubject.peek().get().getIdentifier()));

    }

    @Test
    public void testMultipleMessageConsumptionOrdering() throws InterruptedException {
        List<GenericDomainEventMessage<String>> messages = Arrays.asList(domainMessage("0"),
                                                                                           domainMessage("3"),
                                                                                           domainMessage("1"),
                                                                                           domainMessage("4"),
                                                                                           domainMessage("6"),
                                                                                           domainMessage("2"),
                                                                                           domainMessage("9"),
                                                                                           domainMessage("5")
        );

        messages.forEach(m -> {
            UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(m);
            eventBus.publish(m);
            uow.commit();
        });


        Consumer<String, byte[]> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singleton(SOME_TOPIC));
        KafkaMessageStream<String, byte[]> testSubject = new KafkaMessageStream<>(null, consumer, messageConverter);

        testSubject.hasNextAvailable(100, TimeUnit.MILLISECONDS);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (testSubject.hasNextAvailable(1, TimeUnit.SECONDS)) {
                String identifier = testSubject.nextAvailable().getPayload().toString();
                ids.add(identifier);
            }
        }

        System.out.println(ids);

        assertEquals("kafka stream should fetch all messages", 8, ids.size());
        assertThat(ids, IsIterableContainingInOrder.contains("0", "3", "1", "4", "6", "2", "9", "5"));
    }



    //TODO: test when we exceed batch size

    //TODO: integration test with TokenProcessor (end2end)

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        kafkaPublisher.shutDown();
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
        properties.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "org.axonframework.kafka.eventhandling.consumer.TestPartitioner");
        return properties;
    }

    private List<GenericDomainEventMessage<String>> domainMessages(String aggregateId, int limit) {
        return IntStream.range(0, limit)
                        .mapToObj(i -> domainMessage(aggregateId))
                        .collect(Collectors.toList());
    }

    private GenericDomainEventMessage<String> domainMessage(String aggregateId) {
        return new GenericDomainEventMessage<>("Stub", aggregateId, 1L, aggregateId, MetaData.with("key", "value"));
    }*/
}