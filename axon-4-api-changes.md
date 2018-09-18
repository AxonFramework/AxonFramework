Major API Changes
=================

Other changes
=============

* TrackingToken removed from `AnnotatedSaga` and `SagaStore` implementations

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

### Removed classes
|                           Class                    |             Why                     |
|----------------------------------------------------|-------------------------------------|
| org.axonframework.serialization.MessageSerializer  | All messages are serializable now.  |
| org.axonframework.serialization.SerializationAware | All messages are serializable now.  |

### Classes for which the Constructor has been replaced for a Builder

- org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter
- org.axonframework.jgroups.commandhandling.JGroupsConnector
- org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter
- org.axonframework.springcloud.commandhandling.SpringCloudHttpBackupCommandRouter
- org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector
