# Design principles

## Base
- Use JDK17.
- Project Reactor throughout APIs.
- Decide upon project modularity. 
- E.g. should we split axon-messaging into events/commands/queries? Of move core components to axon-core?.

## UnitOfWork
- UnitOfWork should not be accessible to end users. 
  Instead, they should only be able to interact with its lifecycle (e.g., add commit-phase operations) and context (to add resources).
  This guards against users accidentally invoking, for example, UnitOfWork#commit (which they should never do).

## Messaging
- Dispatch Interceptors should allow reaction to the responses of handling the message(s).
- Disconnect message name from payload type. 
  This means during handler subscription, that you need to provide a name. 
  Annotation based may default to the FQCN, still.
- Make the notion of 'namespaces' to all messages explicit. 
  This is already present at the moment, but it's part of the payloadType. 
  Exposes this directly allows a (cleaner) mapping from messages-to-namespace, and namespaces-to-context.
- A Message Handler should be capable of defining the business name of the message it handles,
   and the type it wants to receive it in.

## Event Processing
- 

## Configuration
- Break up Configuration module, to not have one module that depends on all other modules.
- Define Message Handling Component configuration (MHC-configuration), without Annotations.
- Drop default Serializer, to enforce users to think about the Serializer to use.
- Revamp the configuration to allow a 'higher-level' configuration component,
   like a "Command Handling Component Configuration" or "Command Center Configuration".
  Through this, we can have a user define a message handler, appending any type of additional behavior required.
  Furthermore, this allows us to eliminate unclear config options (e.g., why have a Parameter Resolver for the Repository?).
  Instead, we are then able to take the users configuration, 
   and wrap the behavior of the infrastructure components.
  Simply put, use the Decorator Pattern.
- Favor direct component configuration i.o. Service Loader usage.

## Annotations
- Define annotation-based Message Handling Component setup, using the MHC-configuration
- HandlerEnhancers and ParameterResolvers are purely intended for annotation based MHCs.

## Serialization
- Messages should not be serialization native. 
  The message buses need to be serialization aware, though. 
  They should, as these know the message handlers, and what the expected type to handle is. 
  Thus, handlers need to register themselves with the desired message name.
- Attach upcasting to the serialization-process / Serializer.
- Enforce serialized format of internal objects, e.g. tokens.
  This eliminates issues with de-/serialization with different Serializer choices.
- Serializers are configured on the buses.

## Snapshotting
- Snapshot triggering, creation, and usage should be more easily definable by the end user
  This point stems from the assumed lack of XStream serialization simplicity, that "simply works."
  Using another format, like Jackson, currently requires introduction of getter/setter logic; code that doesn't belong in an Aggregate.
- Employee snapshotting in test fixtures.

## Testing
- Have Aggregate Test Fixtures ingest the Aggregate Configuration, to base the test suite on.
- Aggregate Test Fixtures should, if configured, validate the given scenario's state with the snapshot state.
  Doing so, we guard users against incorrectly defining the snapshot state of their aggregates.

## Rules
- No ThreadLocals!
- No XStream! 
- No static methods on our public APIs!
- No locks / synchronized keywords!
- No Thread#sleep!
- No Exception throwing in the functional-coding style!
- No Schema maintenance!
