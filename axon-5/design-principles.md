# Design principles

## Base
- JDK17
- Project Reactor throughout APIs
- Decide upon project modularity (e.g., should we split axon-messaging into events/commands/queries? Of move core components to axon-core?).

## UnitOfWork
- UnitOfWork should not be accessible to end users. Instead, they should only be able to interact with its lifecycle (e.g., add commit-phase operations) and context (to add resourcers).

## Messaging
- Dispatch Interceptors should allow reaction to the responses of handling the message(s).
- Disconnect message name from payload type. This means during handler subscription, that you need to provide a name. Annotation based may default to the FQCN, still.
- Make the notion of 'namespaces' to all messages explicit. This is already present at the moment, but it's part of the payloadType. Exposes this directly allows a (cleaner) mapping from messages-to-namespace, and namespaces-to-context.

## Configuration
- Break up Configuration module, to not have one module that depends on all other modules
- Define Message Handling Component configuration (MHC-configuration), without Annotations

## Annotations
- Define annotation-based Message Handling Component setup, using the MHC-configuration
- HandlerEnhancers and ParameterResolvers are purely intended for annotation based MHCs.

## Serialization
- Messages should not be serialization native. The message buses need to be serialization aware, though. They should, as these know the message handlers, and what the expected type to handle is. Thus, handlers need to register themselves with the desired payload type.
- Attach upcasting to the serialization-process / Serializer

## Rules
- No ThreadLocals
- No XStream