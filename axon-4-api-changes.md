Major API Changes
=================

Other changes
=============

* TrackingToken removed from `AnnotatedSaga` and `SagaStore` implementations

### Moved classes

|               Axon 3                      |                    Axon 4                      |
|-------------------------------------------|------------------------------------------------|
| org.axonframework.messaging.MessageStream | org.axonframework.common.stream.BlockingStream |
| org.axonframework.messaging.StreamUtils   | org.axonframework.common.stream.StreamUtils    |

### Removed classes

|                           Class                                       |                       Why                     |
|-----------------------------------------------------------------------------------------------------------------------|
| org.axonframework.commandhandling.disruptor.DisruptorConfiguration    | Removed in favor DisruptorCommandBus.Builder  |

### Classes for which the Constructor has been replaced for a Builder

- org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter
- org.axonframework.jgroups.commandhandling.JGroupsConnector
- org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter
- org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter
- org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector
- org.axonframework.commandhandlingAsynchronousCommandBus
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