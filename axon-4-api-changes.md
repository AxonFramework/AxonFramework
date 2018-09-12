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

### Classes for which the Constructor has been replaced for a Builder

- org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter
- org.axonframework.jgroups.commandhandling.JGroupsConnector
- org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter
- org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter
- org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector
