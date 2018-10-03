Major API Changes
=================

Other changes
=============

* TrackingToken removed from `AnnotatedSaga` and `SagaStore` implementations
* The org.axonframework.kafka.eventhandling.consumer.AsyncFetcher it's Builder solution has been made equal to the other
Builder implementations introduced. This entails the following changes:
 - The AsyncFetcher constructor has been made protected for overriding
 - The AsyncFetcher#builder(Map<String, Object>) function is removed in favor of AsyncFetcher.Builder#consumerFactory(Map<String, Object>)
 - The AsyncFetcher#builder(ConsumerFactory<K, V>) function is removed in favor of AsyncFetcher.Builder#consumerFactory(ConsumerFactory<K, V>)
 - A AsyncFetcher#builder() is added to instantiate the AsyncFetcher.Builder
 - AsyncFetcher.Builder#withPool(ExecutorService) has been renamed to AsyncFetcher.Builder#executorService(ExecutorService) 
 - AsyncFetcher.Builder#withMessageConverter(KafkaMessageConverter<K, V>) has been renamed to AsyncFetcher.Builder#messageConverter(KafkaMessageConverter<K, V>) 
 - AsyncFetcher.Builder#withBufferFactory(Supplier<Buffer<KafkaEventMessage>>) has been renamed to AsyncFetcher.Builder#bufferFactory(Supplier<Buffer<KafkaEventMessage>>) 
 - AsyncFetcher.Builder#withTopic(String) has been renamed to AsyncFetcher.Builder#topic(String) 
 - AsyncFetcher.Builder#onRecordPublished(BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void>) has been renamed to AsyncFetcher.Builder#consumerRecordCallback(BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void>) 
 - AsyncFetcher.Builder#withPollTimeout(long, TimeUnit) has been renamed to AsyncFetcher.Builder#pollTimeout(long, TimeUnit)
* The org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory it's Builder solution has been made equal to 
the other Builder implementations introduced. This entails the following changes:
 - The DefaultProducerFactory constructor has been made protected for overriding
 - The DefaultProducerFactory#builder(Map<String, Object>) function is removed in favor of DefaultProducerFactory.Builder#configuration(Map<String, Object>)
 - A DefaultProducerFactory#builder() is added to instantiate the DefaultProducerFactory.Builder
 - DefaultProducerFactory.Builder#withCloseTimeout(int, TimeUnit) has been renamed to DefaultProducerFactory.Builder#closeTimeout(int, TimeUnit) 
 - DefaultProducerFactory.Builder#withProducerCacheSize(int) has been renamed to DefaultProducerFactory.Builder#producerCacheSize(int) 
 - DefaultProducerFactory.Builder#withConfirmationMode(ConfirmationMode) has been renamed to DefaultProducerFactory.Builder#confirmationMode(ConfirmationMode) 
 - DefaultProducerFactory.Builder#withTransactionalIdPrefix(String) has been renamed to DefaultProducerFactory.Builder#transactionalIdPrefix(String)
* Renamed CommitEntryConfiguration.Builder functions to align with new builder approach:
 - withFirstTimestampProperty(String) -> firstTimestampProperty(String) 
 - withLastTimestampProperty(String) -> lastTimestampProperty(String) 
 - withFirstSequenceNumberProperty(String) -> firstSequenceNumberProperty(String) 
 - withLastSequenceNumberProperty(String) -> lastSequenceNumberProperty(String) 
 - withEventsProperty(String) -> eventsProperty(String)  
* Renamed EventEntryConfiguration.Builder functions to align with new builder approach:
 - withTimestampProperty(String) -> timestampProperty(String)
 - withEventIdentifierProperty(String) -> eventIdentifierProperty(String)
 - withAggregateIdentifierProperty(String) -> aggregateIdentifierProperty(String)
 - withSequenceNumberProperty(String) -> sequenceNumberProperty(String)
 - withTypeProperty(String) -> typeProperty(String)
 - withPayloadTypeProperty(String) -> payloadTypeProperty(String)
 - withPayloadRevisionProperty(String) -> payloadRevisionProperty(String)
 - withPayloadProperty(String) -> payloadProperty(String)
 - withMetaDataProperty(String) -> metaDataProperty(String)
* Renamed EventSchema.Builder functions to align with the new builder approach:
 - withEventTable(String) -> eventTable(String)
 - withSnapshotTable(String) -> snapshotTable(String)
 - withGlobalIndexColumn(String) -> globalIndexColumn(String)
 - withTimestampColumn(String) -> timestampColumn(String)
 - withEventIdentifierColumn(String) -> eventIdentifierColumn(String)
 - withAggregateIdentifierColumn(String) -> aggregateIdentifierColumn(String)
 - withSequenceNumberColumn(String) -> sequenceNumberColumn(String)
 - withTypeColumn(String) -> typeColumn(String)
 - withPayloadTypeColumn(String) -> payloadTypeColumn(String)
 - withPayloadRevisionColumn(String) -> payloadRevisionColumn(String)
 - withPayloadColumn(String) -> payloadColumn(String)
 - withMetaDataColumn(String) -> metaDataColumn(String)

### Moved classes

|                                 Axon 3                                        |                                  Axon 4                                    |
|-------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| org.axonframework.messaging.MessageStream                                     | org.axonframework.common.stream.BlockingStream                             |
| org.axonframework.messaging.StreamUtils                                       | org.axonframework.common.stream.StreamUtils                                |
| org.axonframework.queryhandling.responsetypes.AbstractResponseType            | org.axonframework.messaging.responsetypes.AbstractResponseType             |
| org.axonframework.queryhandling.responsetypes.InstanceResponseType            | org.axonframework.messaging.responsetypes.InstanceResponseType             |
| org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType   | org.axonframework.messaging.responsetypes.MultipleInstancesResponseType    |
| org.axonframework.queryhandling.responsetypes.ResponseType                    | org.axonframework.messaging.responsetypes.ResponseType                     |
| org.axonframework.queryhandling.responsetypes.ResponseTypes                   | org.axonframework.messaging.responsetypes.ResponseTypes                    |
| org.axonframework.boot.autoconfig.KafkaProperties                             | org.axonframework.boot.KafkaProperties                                     |

### Removed classes
|                           Class                    |             Why                     |
|----------------------------------------------------|-------------------------------------|
| org.axonframework.serialization.MessageSerializer  | All messages are serializable now.  |
| org.axonframework.serialization.SerializationAware | All messages are serializable now.  |

### Removed classes

|                                   Class                                       |                       Why                     |
|-------------------------------------------------------------------------------|-----------------------------------------------|
| org.axonframework.commandhandling.disruptor.DisruptorConfiguration            | Removed in favor DisruptorCommandBus.Builder  |
| org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration    | Removed in favor KafkaPublisher.Builder       |

### Classes for which the Constructor has been replaced for a Builder

- org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter
- org.axonframework.jgroups.commandhandling.JGroupsConnector
- org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter
- org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter
- org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector
- org.axonframework.commandhandling.AsynchronousCommandBus
- org.axonframework.commandhandling.SimpleCommandBus
- org.axonframework.commandhandling.disruptor.DisruptorCommandBus
- org.axonframework.commandhandling.distributed.DistributedCommandBus
- org.axonframework.commandhandling.gateway.AbstractCommandGateway
- org.axonframework.commandhandling.gatewayCommandGatewayFactory.GatewayInvocationHandler
- org.axonframework.commandhandling.gatewayCommandGatewayFactory.DispatchOnInvocationHandler
- org.axonframework.commandhandling.gateway.DefaultCommandGateway
- org.axonframework.commandhandling.model.AbstractRepository
- org.axonframework.commandhandling.model.LockingRepository
- org.axonframework.commandhandling.model.GenericJpaRepository
- org.axonframework.eventsourcing.EventSourcingRepository
- org.axonframework.eventsourcing.CachingEventSourcingRepository
- org.axonframework.commandhandling.AggregateAnnotationCommandHandler
- org.axonframework.deadline.quartz.QuartzDeadlineManager
- org.axonframework.deadline.SimpleDeadlineManager
- org.axonframework.eventhandling.scheduling.java.SimpleEventScheduler
- org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler
- org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore
- org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore
- org.axonframework.eventhandling.saga.repository.jdbc.JdbcSagaStore
- org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore
- org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository
- org.axonframework.eventhandling.saga.repository.CachingSagaStore
- org.axonframework.eventhandling.saga.repository.LockingSagaRepository
- org.axonframework.eventhandling.saga.AbstractSagaManager
- org.axonframework.eventhandling.saga.AnnotatedSagaManager
- org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter
- org.axonframework.kafka.eventhandling.consumer.AsyncFetcher
- org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory
- org.axonframework.kafka.eventhandling.producer.KafkaPublisher
- org.axonframework.mongo.eventhandling.saga.repository.MongoSagaStore
- org.axonframework.mongo.eventsourcing.tokenstore.MongoTokenStore
- org.axonframework.mongo.AbstractMongoTemplate
- org.axonframework.mongo.DefaultMongoTemplate
- org.axonframework.queryhandling.DefaultQueryGateway
- org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler
- org.axonframework.serialization.json.JacksonSerializer
- org.axonframework.serialization.JavaSerializer
- org.axonframework.serialization.AbstractXStreamSerializer
- org.axonframework.mongo.serialization.DBObjectXStreamSerializer
- org.axonframework.serialization.xml.XStreamSerializer
- org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine
- org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine
- org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine
- org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
- org.axonframework.mongo.eventsourcing.eventstore.MongoEventStorageEngine
- org.axonframework.eventsourcing.AbstractSnapshotter
- org.axonframework.eventsourcing.AggregateSnapshotter
- org.axonframework.spring.eventsourcing.SpringAggregateSnapshotter
- org.axonframework.eventsourcing.eventstore.AbstractEventStore
- org.axonframework.eventsourcing.eventstore.EmbeddedEventStore