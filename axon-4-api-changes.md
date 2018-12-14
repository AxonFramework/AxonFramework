Major API Changes
=================

## Defaulting to tracking processors

When an Event Store (or Event Bus implementing `StreamableMessageSource`) is configured, Axon will default to using 
tracking processors. This may be overridden using the `EventProcessingConfigurer.usingSubscribingProcessors()` method.

In Axon 4, the Event Bus itself no longer implements `StreamableMessageSource` (as was the case in Axon 3), meaning that
using the `SimpleEventBus` will also imply subscribing processors as the default.

## Serialization
Instead of throwing an `UnknownSerializedTypeException`, serializers now return an `UnknownSerializedType` object, 
which provides access to the raw data in any supported intermediate representation, such as `JsonNode` or Dom4J 
`Document`.

## New Module Structure

We noticed that `axon-core` was encompassing to big a portion of the whole framework. Thus, we have split it into a 
couple of reasonable components. It's been split into `axon-messaging`, `axon-modelling`, `axon-eventsourcing`, 
`axon-disruptor` and `axon-configuration`.

The Spring Cloud, AMQP, Kafka, Mongo and JGroups related modules are now published under another Maven Group ID, to 
emphasise their status as an extension to the framework, rather than a core module. This also means their release 
schedule may differ slightly from that of the core framework.

The new Group ID's are:

| Module  | New Group ID                             |
|---------|------------------------------------------|
| Spring  | org.axonframework.extensions.springcloud |
| AMQP    | org.axonframework.extensions.amqp        |
| Kafka   | org.axonframework.extensions.kafka       |
| JGroups | org.axonframework.extensions.jgroups     |
| Mongo   | org.axonframework.extensions.mongo       |

Other changes
=============

* TrackingToken removed from `AnnotatedSaga` and `SagaStore` implementations
* EventProcessingConfiguration represents an interface for accessing event processing components
* SagaConfiguration is a configuration component carrier only - does not start any processors
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
* Renamed AbstractEventStorageEngine#getSerializer() to AbstractEventStorageEngine#getSnapshotSerializer()
* Renamed SimpleEventHandlerInvoker#eventListeners() to SimpleEventHandlerInvoker#eventHandlers()

### Command Message Routing
 
Introduced the @RoutingKey annotation, which is an annotation of the @TargetAggregateIdentifier.
Additionally, adjusted the AnnotationRoutingStrategy to check for a (meta-)annotated RoutingKey field i.o. 
@TargetAggregateIdentifier.

### Domain Event Sequences

The contract to be able to provide the last known sequence for a given aggregate identifier has been moved from the
EventStore to a (new) interface, the DomainEventSequenceAware.

### Moved classes

|                                 Axon 3                                                                        |                                  Axon 4                                                                   |
|---------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| org.axonframework.messaging.MessageStream                                                                     | org.axonframework.common.stream.BlockingStream                                                            |
| org.axonframework.messaging.StreamUtils                                                                       | org.axonframework.common.stream.StreamUtils                                                               |
| org.axonframework.queryhandling.responsetypes.AbstractResponseType                                            | org.axonframework.messaging.responsetypes.AbstractResponseType                                            |
| org.axonframework.queryhandling.responsetypes.InstanceResponseType                                            | org.axonframework.messaging.responsetypes.InstanceResponseType                                            |
| org.axonframework.queryhandling.responsetypes.MultipleInstancesResponseType                                   | org.axonframework.messaging.responsetypes.MultipleInstancesResponseType                                   |
| org.axonframework.queryhandling.responsetypes.ResponseType                                                    | org.axonframework.messaging.responsetypes.ResponseType                                                    |
| org.axonframework.queryhandling.responsetypes.ResponseTypes                                                   | org.axonframework.messaging.responsetypes.ResponseTypes                                                   |
| org.axonframework.boot.autoconfig.KafkaProperties                                                             | org.axonframework.boot.KafkaProperties                                                                    |
| org.axonframework.commandhandling.disruptor.AggregateBlacklistedException                                     | org.axonframework.disruptor.commandhandling.AggregateBlacklistedException                                 |
| org.axonframework.commandhandling.disruptor.AggregateStateCorruptedException                                  | org.axonframework.disruptor.commandhandling.AggregateStateCorruptedException                              |
| org.axonframework.commandhandling.disruptor.BlacklistDetectingCallback                                        | org.axonframework.disruptor.commandhandling.BlacklistDetectingCallback                                    |
| org.axonframework.commandhandling.disruptor.CommandHandlerInvoker                                             | org.axonframework.disruptor.commandhandling.CommandHandlerInvoker                                         |
| org.axonframework.commandhandling.disruptor.CommandHandlingEntry                                              | org.axonframework.disruptor.commandhandling.CommandHandlingEntry                                          |
| org.axonframework.commandhandling.disruptor.DisruptorCommandBus                                               | org.axonframework.disruptor.commandhandling.DisruptorCommandBus                                           |
| org.axonframework.commandhandling.disruptor.DisruptorUnitOfWork                                               | org.axonframework.disruptor.commandhandling.DisruptorUnitOfWork                                           |
| org.axonframework.commandhandling.disruptor.EventPublisher                                                    | org.axonframework.disruptor.commandhandling.EventPublisher                                                |
| org.axonframework.commandhandling.disruptor.FirstLevelCache                                                   | org.axonframework.disruptor.commandhandling.FirstLevelCache                                               |
| org.axonframework.eventsourcing.eventstore.AbstractDomainEventEntry                                           | org.axonframework.eventhandling.AbstractDomainEventEntry                                                  |
| org.axonframework.eventsourcing.eventstore.AbstractEventEntry                                                 | org.axonframework.eventhandling.AbstractEventEntry                                                        |
| org.axonframework.eventsourcing.eventstore.AbstractSequencedDomainEventEntry                                  | org.axonframework.eventhandling.AbstractSequencedDomainEventEntry                                         |
| org.axonframework.eventsourcing.eventstore.DomainEventData                                                    | org.axonframework.eventhandling.DomainEventData                                                           |
| org.axonframework.eventsourcing.DomainEventMessage                                                            | org.axonframework.eventhandling.DomainEventMessage                                                        |
| org.axonframework.eventsourcing.eventstore.EventData                                                          | org.axonframework.eventhandling.EventData                                                                 |
| org.axonframework.eventsourcing.eventstore.EventUtils                                                         | org.axonframework.eventhandling.EventUtils                                                                |
| org.axonframework.eventsourcing.eventstore.GenericDomainEventEntry                                            | org.axonframework.eventhandling.GenericDomainEventEntry                                                   |
| org.axonframework.eventsourcing.GenericDomainEventMessage                                                     | org.axonframework.eventhandling.GenericDomainEventMessage                                                 |
| org.axonframework.eventsourcing.GenericTrackedDomainEventMessage                                              | org.axonframework.eventhandling.GenericTrackedDomainEventMessage                                          |
| org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken                                        | org.axonframework.eventhandling.GlobalSequenceTrackingToken                                               |
| org.axonframework.eventsourcing.SequenceNumber                                                                | org.axonframework.eventhandling.SequenceNumber                                                            |
| org.axonframework.eventsourcing.SequenceNumberParameterResolverFactory                                        | org.axonframework.eventhandling.SequenceNumberParameterResolverFactory                                    |
| org.axonframework.eventsourcing.eventstore.TrackedDomainEventData                                             | org.axonframework.eventhandling.TrackedDomainEventData                                                    |
| org.axonframework.eventsourcing.eventstore.TrackedEventData                                                   | org.axonframework.eventhandling.TrackedEventData                                                          |
| org.axonframework.eventsourcing.eventstore.TrackingEventStream                                                | org.axonframework.eventhandling.TrackingEventStream                                                       |
| org.axonframework.eventsourcing.eventstore.TrackingToken                                                      | org.axonframework.eventhandling.TrackingToken                                                             |
| org.axonframework.eventsourcing.eventstore.TrackingTokenParameterResolverFactory                              | org.axonframework.eventhandling.TrackingTokenParameterResolverFactory                                     |
| org.axonframework.commandhandling.conflictresolution.ConflictDescription                                      | org.axonframework.eventsourcing.conflictresolution.ConflictDescription                                    |
| org.axonframework.commandhandling.conflictresolution.ConflictExceptionSupplier                                | org.axonframework.eventsourcing.conflictresolution.ConflictExceptionSupplier                              |
| org.axonframework.commandhandling.conflictresolution.ConflictResolution                                       | org.axonframework.eventsourcing.conflictresolution.ConflictResolution                                     |
| org.axonframework.commandhandling.conflictresolution.ConflictResolver                                         | org.axonframework.eventsourcing.conflictresolution.ConflictResolver                                       |
| org.axonframework.commandhandling.conflictresolution.Conflicts                                                | org.axonframework.eventsourcing.conflictresolution.Conflicts                                              |
| org.axonframework.commandhandling.conflictresolution.ContextAwareConflictExceptionSupplier                    | org.axonframework.eventsourcing.conflictresolution.ContextAwareConflictExceptionSupplier                  |
| org.axonframework.commandhandling.conflictresolution.DefaultConflictDescription                               | org.axonframework.eventsourcing.conflictresolution.DefaultConflictDescription                             |
| org.axonframework.commandhandling.conflictresolution.DefaultConflictResolver                                  | org.axonframework.eventsourcing.conflictresolution.DefaultConflictResolver                                |
| org.axonframework.commandhandling.conflictresolution.NoConflictResolver                                       | org.axonframework.eventsourcing.conflictresolution.NoConflictResolver                                     |
| org.axonframework.commandhandling.AggregateAnnotationCommandHandler                                           | org.axonframework.modelling.command.AggregateAnnotationCommandHandler                                     |
| org.axonframework.commandhandling.AnnotationCommandTargetResolver                                             | org.axonframework.modelling.command.AnnotationCommandTargetResolver                                       |
| org.axonframework.commandhandling.CommandTargetResolver                                                       | org.axonframework.modelling.command.CommandTargetResolver                                                 |
| org.axonframework.commandhandling.MetaDataCommandTargetResolver                                               | org.axonframework.modelling.command.MetaDataCommandTargetResolver                                         |
| org.axonframework.commandhandling.TargetAggregateIdentifier                                                   | org.axonframework.modelling.command.TargetAggregateIdentifier                                             |
| org.axonframework.commandhandling.TargetAggregateVersion                                                      | org.axonframework.modelling.command.TargetAggregateVersion                                                |
| org.axonframework.commandhandling.VersionedAggregateIdentifier                                                | org.axonframework.modelling.command.VersionedAggregateIdentifier                                          |
| org.axonframework.commandhandling.model.AbstractRepository                                                    | org.axonframework.modelling.command.AbstractRepository                                                    |
| org.axonframework.commandhandling.model.Aggregate                                                             | org.axonframework.modelling.command.Aggregate                                                             |
| org.axonframework.commandhandling.model.AggregateAnnotationCommandHandler                                     | org.axonframework.modelling.command.AggregateAnnotationCommandHandler                                     |
| org.axonframework.commandhandling.model.AggregateEntityNotFoundException                                      | org.axonframework.modelling.command.AggregateEntityNotFoundException                                      |
| org.axonframework.commandhandling.model.AggregateIdentifier                                                   | org.axonframework.modelling.command.AggregateIdentifier                                                   |
| org.axonframework.commandhandling.model.AggregateInvocationException                                          | org.axonframework.modelling.command.AggregateInvocationException                                          |
| org.axonframework.commandhandling.model.AggregateLifecycle                                                    | org.axonframework.modelling.command.AggregateLifecycle                                                    |
| org.axonframework.commandhandling.model.AggregateMember                                                       | org.axonframework.modelling.command.AggregateMember                                                       |
| org.axonframework.commandhandling.model.AggregateNotFoundException                                            | org.axonframework.modelling.command.AggregateNotFoundException                                            |
| org.axonframework.commandhandling.model.AggregateRolledBackException                                          | org.axonframework.modelling.command.AggregateRolledBackException                                          |
| org.axonframework.commandhandling.model.AggregateRoot                                                         | org.axonframework.modelling.command.AggregateRoot                                                         |
| org.axonframework.commandhandling.model.AggregateScopeDescriptor                                              | org.axonframework.modelling.command.AggregateScopeDescriptor                                              |
| org.axonframework.commandhandling.model.AggregateVersion                                                      | org.axonframework.modelling.command.AggregateVersion                                                      |
| org.axonframework.commandhandling.model.ApplyMore                                                             | org.axonframework.modelling.command.ApplyMore                                                             |
| org.axonframework.commandhandling.model.CommandHandlerInterceptor                                             | org.axonframework.modelling.command.CommandHandlerInterceptor                                             |
| org.axonframework.commandhandling.model.ConcurrencyException                                                  | org.axonframework.modelling.command.ConcurrencyException                                                  |
| org.axonframework.commandhandling.model.ConflictingAggregateVersionException                                  | org.axonframework.modelling.command.ConflictingAggregateVersionException                                  |
| org.axonframework.commandhandling.model.ConflictingModificationException                                      | org.axonframework.modelling.command.ConflictingModificationException                                      |
| org.axonframework.commandhandling.model.EntityId                                                              | org.axonframework.modelling.command.EntityId                                                              |
| org.axonframework.commandhandling.model.ForwardingMode                                                        | org.axonframework.modelling.command.ForwardingMode                                                        |
| org.axonframework.commandhandling.model.ForwardMatchingInstances                                              | org.axonframework.modelling.command.ForwardMatchingInstances                                              |
| org.axonframework.commandhandling.model.ForwardNone                                                           | org.axonframework.modelling.command.ForwardNone                                                           |
| org.axonframework.commandhandling.model.LockingRepository                                                     | org.axonframework.modelling.command.LockingRepository                                                     |
| org.axonframework.commandhandling.model.LockAwareAggregate                                                    | org.axonframework.modelling.command.LockAwareAggregate                                                    |
| org.axonframework.commandhandling.model.GenericJpaRepository                                                  | org.axonframework.modelling.command.GenericJpaRepository                                                  |
| org.axonframework.commandhandling.model.ForwardToAll                                                          | org.axonframework.modelling.command.ForwardToAll                                                          |
| org.axonframework.commandhandling.model.Repository                                                            | org.axonframework.modelling.command.Repository                                                            |
| org.axonframework.commandhandling.model.RepositoryProvider                                                    | org.axonframework.modelling.command.RepositoryProvider                                                    |
| org.axonframework.commandhandling.model.inspection.AbstractChildEntityDefinition                              | org.axonframework.modelling.command.inspection.AbstractChildEntityDefinition                              |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityCollectionDefinition    | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityCollectionDefinition    |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityDefinition              | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityDefinition              |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityMapDefinition           | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityMapDefinition           |
| org.axonframework.commandhandling.model.inspection.AggregateMetaModelFactory                                  | org.axonframework.modelling.command.inspection.AggregateMetaModelFactory                                  |
| org.axonframework.commandhandling.model.inspection.AggregateModel                                             | org.axonframework.modelling.command.inspection.AggregateModel                                             |
| org.axonframework.commandhandling.model.inspection.AnnotatedAggregate                                         | org.axonframework.modelling.command.inspection.AnnotatedAggregate                                         |
| org.axonframework.commandhandling.model.inspection.AnnotatedAggregateMetaModelFactory                         | org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory                         |
| org.axonframework.commandhandling.model.inspection.AnnotatedChildEntity                                       | org.axonframework.modelling.command.inspection.AnnotatedChildEntity                                       |
| org.axonframework.commandhandling.model.inspection.AnnotatedCommandHandlerInterceptor                         | org.axonframework.modelling.command.inspection.AnnotatedCommandHandlerInterceptor                         |
| org.axonframework.commandhandling.model.inspection.ChildEntity                                                | org.axonframework.modelling.command.inspection.ChildEntity                                                |
| org.axonframework.commandhandling.model.inspection.ChildEntityDefinition                                      | org.axonframework.modelling.command.inspection.ChildEntityDefinition                                      |
| org.axonframework.commandhandling.model.inspection.ChildForwardingCommandMessageHandlingMember                | org.axonframework.modelling.command.inspection.ChildForwardingCommandMessageHandlingMember                |
| org.axonframework.commandhandling.model.inspection.CommandHandlerInterceptorHandlingMember                    | org.axonframework.modelling.command.inspection.CommandHandlerInterceptorHandlingMember                    |
| org.axonframework.commandhandling.model.inspection.EntityModel                                                | org.axonframework.modelling.command.inspection.EntityModel                                                |
| org.axonframework.commandhandling.model.inspection.MethodCommandHandlerInterceptorDefinition                  | org.axonframework.modelling.command.inspection.MethodCommandHandlerInterceptorDefinition                  |
| org.axonframework.deadline.AbstractDeadlineManager                                                            | org.axonframework.deadline.AbstractDeadlineManager                                                        |
| org.axonframework.deadline.DeadlineException                                                                  | org.axonframework.deadline.DeadlineException                                                              |
| org.axonframework.deadline.DeadlineManager                                                                    | org.axonframework.deadline.DeadlineManager                                                                |
| org.axonframework.deadline.DeadlineMessage                                                                    | org.axonframework.deadline.DeadlineMessage                                                                |
| org.axonframework.deadline.GenericDeadlineMessage                                                             | org.axonframework.deadline.GenericDeadlineMessage                                                         |
| org.axonframework.deadline.SimpleDeadlineManager                                                              | org.axonframework.deadline.SimpleDeadlineManager                                                          |
| org.axonframework.deadline.annotation.DeadlineHandler                                                         | org.axonframework.deadline.annotation.DeadlineHandler                                                     |
| org.axonframework.deadline.annotation.DeadlineHandlingMember                                                  | org.axonframework.deadline.annotation.DeadlineHandlingMember                                              |
| org.axonframework.deadline.annotation.DeadlineMethodMessageHandlerDefinition                                  | org.axonframework.deadline.annotation.DeadlineMethodMessageHandlerDefinition                              |
| org.axonframework.deadline.quartz.DeadlineJob                                                                 | org.axonframework.deadline.quartz.DeadlineJob                                                             |
| org.axonframework.deadline.quartz.QuartzDeadlineManager                                                       | org.axonframework.deadline.quartz.QuartzDeadlineManager                                                   |
| org.axonframework.deadline.quartz.DeadlineJobDataBinderTest                                                   | org.axonframework.deadline.quartz.DeadlineJobDataBinderTest                                               |
| org.axonframework.commandhandling.model.inspection.AbstractChildEntityDefinition                              | org.axonframework.modelling.command.inspection.AbstractChildEntityDefinition                              |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityCollectionDefinition    | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityCollectionDefinition    |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityDefinition              | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityDefinition              |
| org.axonframework.commandhandling.model.inspection.AggregateMemberAnnotatedChildEntityMapDefinition           | org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityMapDefinition           |
| org.axonframework.commandhandling.model.inspection.AggregateMetaModelFactory                                  | org.axonframework.modelling.command.inspection.AggregateMetaModelFactory                                  |
| org.axonframework.commandhandling.model.inspection.AggregateModel                                             | org.axonframework.modelling.command.inspection.AggregateModel                                             |
| org.axonframework.commandhandling.model.inspection.AnnotatedAggregate                                         | org.axonframework.modelling.command.inspection.AnnotatedAggregate                                         |
| org.axonframework.commandhandling.model.inspection.AnnotatedAggregateMetaModelFactory                         | org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory                         |
| org.axonframework.commandhandling.model.inspection.AnnotatedChildEntity                                       | org.axonframework.modelling.command.inspection.AnnotatedChildEntity                                       |
| org.axonframework.commandhandling.model.inspection.AnnotatedCommandHandlerInterceptor                         | org.axonframework.modelling.command.inspection.AnnotatedCommandHandlerInterceptor                         |
| org.axonframework.commandhandling.model.inspection.ChildEntity                                                | org.axonframework.modelling.command.inspection.ChildEntity                                                |
| org.axonframework.commandhandling.model.inspection.ChildEntityDefinition                                      | org.axonframework.modelling.command.inspection.ChildEntityDefinition                                      |
| org.axonframework.commandhandling.model.inspection.ChildForwardingCommandMessageHandlingMember                | org.axonframework.modelling.command.inspection.ChildForwardingCommandMessageHandlingMember                |
| org.axonframework.commandhandling.model.inspection.CommandHandlerInterceptorHandlingMember                    | org.axonframework.modelling.command.inspection.CommandHandlerInterceptorHandlingMember                    |
| org.axonframework.commandhandling.model.inspection.EntityModel                                                | org.axonframework.modelling.command.inspection.EntityModel                                                |
| org.axonframework.commandhandling.model.inspection.MethodCommandHandlerInterceptorDefinition                  | org.axonframework.modelling.command.inspection.MethodCommandHandlerInterceptorDefinition                  |
| org.axonframework.eventhandling.saga.AbstractResourceInjector                                                 | org.axonframework.modelling.saga.AbstractResourceInjector                                                 |
| org.axonframework.eventhandling.saga.AbstractSagaManager                                                      | org.axonframework.modelling.saga.AbstractSagaManager                                                      |
| org.axonframework.eventhandling.saga.AnnotatedSaga                                                            | org.axonframework.modelling.saga.AnnotatedSaga                                                            |
| org.axonframework.eventhandling.saga.AnnotatedSagaManager                                                     | org.axonframework.modelling.saga.AnnotatedSagaManager                                                     |
| org.axonframework.eventhandling.saga.AssociationResolver                                                      | org.axonframework.modelling.saga.AssociationResolver                                                      |
| org.axonframework.eventhandling.saga.AssociationValue                                                         | org.axonframework.modelling.saga.AssociationValue                                                         |
| org.axonframework.eventhandling.saga.AssociationValues                                                        | org.axonframework.modelling.saga.AssociationValues                                                        |
| org.axonframework.eventhandling.saga.AssociationValuesImpl                                                    | org.axonframework.modelling.saga.AssociationValuesImpl                                                    |
| org.axonframework.eventhandling.saga.EndSaga                                                                  | org.axonframework.modelling.saga.EndSaga                                                                  |
| org.axonframework.eventhandling.saga.MetaDataAssociationResolver                                              | org.axonframework.modelling.saga.MetaDataAssociationResolver                                              |
| org.axonframework.eventhandling.saga.PayloadAssociationResolver                                               | org.axonframework.modelling.saga.PayloadAssociationResolver                                               |
| org.axonframework.eventhandling.saga.ResourceInjector                                                         | org.axonframework.modelling.saga.ResourceInjector                                                         |
| org.axonframework.eventhandling.saga.Saga                                                                     | org.axonframework.modelling.saga.Saga                                                                     |
| org.axonframework.eventhandling.saga.SagaCreationPolicy                                                       | org.axonframework.modelling.saga.SagaCreationPolicy                                                       |
| org.axonframework.eventhandling.saga.SagaEventHandler                                                         | org.axonframework.modelling.saga.SagaEventHandler                                                         |
| org.axonframework.eventhandling.saga.SagaExecutionException                                                   | org.axonframework.modelling.saga.SagaExecutionException                                                   |
| org.axonframework.eventhandling.saga.SagaInitializationPolicy                                                 | org.axonframework.modelling.saga.SagaInitializationPolicy                                                 |
| org.axonframework.eventhandling.saga.SagaInstantiationException                                               | org.axonframework.modelling.saga.SagaInstantiationException                                               |
| org.axonframework.eventhandling.saga.SagaLifecycle                                                            | org.axonframework.modelling.saga.SagaLifecycle                                                            |
| org.axonframework.eventhandling.saga.SagaMethodMessageHandlerDefinition                                       | org.axonframework.modelling.saga.SagaMethodMessageHandlerDefinition                                       |
| org.axonframework.eventhandling.saga.SagaMethodMessageHandlingMember                                          | org.axonframework.modelling.saga.SagaMethodMessageHandlingMember                                          |
| org.axonframework.eventhandling.saga.SagaRepository                                                           | org.axonframework.modelling.saga.SagaRepository                                                           |
| org.axonframework.eventhandling.saga.SagaScopeDescriptor                                                      | org.axonframework.modelling.saga.SagaScopeDescriptor                                                      |
| org.axonframework.eventhandling.saga.SagaStorageException                                                     | org.axonframework.modelling.saga.SagaStorageException                                                     |
| org.axonframework.eventhandling.saga.SimpleResourceInjector                                                   | org.axonframework.modelling.saga.SimpleResourceInjector                                                   |
| org.axonframework.eventhandling.saga.StartSaga                                                                | org.axonframework.modelling.saga.StartSaga                                                                |
| org.axonframework.eventhandling.saga.metamodel.AnnotationSagaMetaModelFactory                                 | org.axonframework.modelling.saga.metamodel.AnnotationSagaMetaModelFactory                                 |
| org.axonframework.eventhandling.saga.metamodel.SagaMetaModelFactory                                           | org.axonframework.modelling.saga.metamodel.SagaMetaModelFactory                                           |
| org.axonframework.eventhandling.saga.metamodel.SagaModel                                                      | org.axonframework.modelling.saga.metamodel.SagaModel                                                      |
| org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository                                       | org.axonframework.modelling.saga.repository.AnnotatedSagaRepository                                       |
| org.axonframework.eventhandling.saga.repository.AssociationValueMap                                           | org.axonframework.modelling.saga.repository.AssociationValueMap                                           |
| org.axonframework.eventhandling.saga.repository.CachingSagaStore                                              | org.axonframework.modelling.saga.repository.CachingSagaStore                                              |
| org.axonframework.eventhandling.saga.repository.LockingSagaRepository                                         | org.axonframework.modelling.saga.repository.LockingSagaRepository                                         |
| org.axonframework.eventhandling.saga.repository.NoResourceInjector                                            | org.axonframework.modelling.saga.repository.NoResourceInjector                                            |
| org.axonframework.eventhandling.saga.repository.SagaCreationException                                         | org.axonframework.modelling.saga.repository.SagaCreationException                                         |
| org.axonframework.eventhandling.saga.repository.SagaStore                                                     | org.axonframework.modelling.saga.repository.SagaStore                                                     |
| org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore                                    | org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore                                    |
| org.axonframework.eventhandling.saga.repository.jdbc.GenericSagaSqlSchema                                     | org.axonframework.modelling.saga.repository.jdbc.GenericSagaSqlSchema                                     |
| org.axonframework.eventhandling.saga.repository.jdbc.HsqlSagaSqlSchema                                        | org.axonframework.modelling.saga.repository.jdbc.HsqlSagaSqlSchema                                        |
| org.axonframework.eventhandling.saga.repository.jdbc.JdbcSagaStore                                            | org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore                                            |
| org.axonframework.eventhandling.saga.repository.jdbc.Oracle11SagaSqlSchema                                    | org.axonframework.modelling.saga.repository.jdbc.Oracle11SagaSqlSchema                                    |
| org.axonframework.eventhandling.saga.repository.jdbc.PostgresSagaSqlSchema                                    | org.axonframework.modelling.saga.repository.jdbc.PostgresSagaSqlSchema                                    |
| org.axonframework.eventhandling.saga.repository.jdbc.SagaSchema                                               | org.axonframework.modelling.saga.repository.jdbc.SagaSchema                                               |
| org.axonframework.eventhandling.saga.repository.jdbc.SagaSqlSchema                                            | org.axonframework.modelling.saga.repository.jdbc.SagaSqlSchema                                            |
| org.axonframework.eventhandling.saga.repository.jpa.AbstractSagaEntry                                         | org.axonframework.modelling.saga.repository.jpa.AbstractSagaEntry                                         |
| org.axonframework.eventhandling.saga.repository.jpa.AssociationValueEntry                                     | org.axonframework.modelling.saga.repository.jpa.AssociationValueEntry                                     |
| org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore                                              | org.axonframework.modelling.saga.repository.jpa.JpaSagaStore                                              |
| org.axonframework.eventhandling.saga.repository.jpa.SagaEntry                                                 | org.axonframework.modelling.saga.repository.jpa.SagaEntry                                                 |
| org.axonframework.eventhandling.saga.repository.jpa.SerializedSaga                                            | org.axonframework.modelling.saga.repository.jpa.SerializedSaga                                            |


### Removed classes
|                           Class                                               |             Why                              |
|-------------------------------------------------------------------------------|----------------------------------------------|
| org.axonframework.serialization.MessageSerializer                             | All messages are serializable now.           |
| org.axonframework.serialization.SerializationAware                            | All messages are serializable now.           |
| org.axonframework.serialization.UnknownSerializedTypeException                | Serializers now return UnknownSerializedType |
| org.axonframework.commandhandling.disruptor.DisruptorConfiguration            | Removed in favor DisruptorCommandBus.Builder |
| org.axonframework.config.EventHandlingConfiguration                           | Removed in favor of EventProcessingModule    |
| org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration    | Removed in favor KafkaPublisher.Builder      |
| org.axonframework.commandhandling.VoidCallback                                | Command execution returns a message now.     |

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
- org.axonframework.eventhandling.AbstractEventBus
- org.axonframework.eventhandling.SimpleEventBus
- org.axonframework.eventhandling.SimpleEventHandlerInvoker
- org.axonframework.eventhandling.AbstractEventProcessor
- org.axonframework.eventhandling.SubscribingEventProcessor
- org.axonframework.eventhandling.TrackingEventProcessor
- org.axonframework.commandhandling.gateway.IntervalRetryScheduler
- org.axonframework.commandhandling.gateway.CommandGatewayFactory
