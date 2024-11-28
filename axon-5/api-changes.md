Version and Dependency Compatibility
====================================

* Axon Framework is no longer based on JDK 8, but on JDK 21 instead.
* Spring Boot 2 is no longer supported. You should upgrade to Spring Boot 3 or higher.
* Spring Framework 5 is no longer supported. You should upgrade to Spring Framework 6 or higher.
* Javax Persistence is completely replaced for Jakarta Persistence. This means the majority of `javax` reference no
  longer apply.

Major API Changes
=================

* **All** code marked as `@Deprecated` in Axon Framework 4 is removed entirely. Each deprecation contains the
  recommended resolution path in the JavaDoc. It is strongly recommended to (1) upgrade to the latest Axon Framework 4
  version, (2) adjust any deprecations from Axon Framework you are using as recommended, and then (3) to make the change
  towards Axon Framework 5.
* The entire API of the `UnitOfWork` has been rewritten to (1) construct an 'async-native' flow to support both an
  imperative and reactive style of programming, (2) eliminate the use of `ThreadLocal`, and (3) protect users from
  internals APIs. This does mean that any direct interaction with the `UnitOfWork` has become a breaking change. Please
  check the [Unit of Work](#unit-of-work) section for more details if you are facing this predicament.
* All message-based infrastructure in Axon Framework will return the `MessageStream` interface. The `MessageStream` is
  intended to support empty results, results of one entry, and results of N entries, thus mirroring Event Handlers (no
  results), Command Handlers (one result), and Query Handlers (N results). Added, the `MessageStream` will function as a
  replacement for components like the `DomainEventStream` and `BlockingStream` on the `EventStore`. As such, the
  `MessageStream` changes **a lot** of (public) APIs within Axon Framework. Please check
  the [Message Stream](#message-stream) section for more details, like an exhaustive list of all the adjusted
  interfaces.
* We no longer support message handler annotated constructors. For example, the constructor of an aggregate can no
  longer contain the `@CommandHandler` annotation. Instead, the `@CreationPolicy` should be used.

## Unit of Work

The `UnitOfWork` interface has been rewritten with roughly three goals in mind:

1. Ensure the API of the `UnitOfWork` easily supports imperative and reactive programming styles.
2. Remove the use of the `ThreadLocal` entirely. This change is paramount for a reactive programming style.
3. Guard users from operations they shouldn't touch. The biggest example of this, was the previous `UnitOfWork#commit`
   operation that **was not** intended to be used by users.

To that end, we broke down the `UnitOfWork` interface into two interfaces and a concrete implementation, being:

1. The `ProcessingLifecycle`, describing methods to register actions into distinct `ProcessingLifeCycle.Phases`, thus
   managing the "lifecycle of a process."
2. The `ProcessingContext`, an implementation of the `ProcessingLifecycle` adding resource management.
3. The `UnitOfWork`, an implementation of the `ProcessingContext` and thus `ProcessingLifecycle`.

The user is intended to interface with the `ProcessingLifecycle` when they need to add actions before/after/during
pre-defined `ProcessingLifecycle.DefaultPhases`.
This will allow us, and them, to customize processes like message handling.
Furthermore, the `ProcessingLifecycle` works with a `CompletableFuture` throughout.

The `ProcessingContext` will in turn provide the space to register resources to be used throughout the
`ProcessingLifecycle`.
Although roughly similar to the previous resource management of the old `UnitOfWork`, we intend this format to replace
the use of the `ThreadLocal`.
As such, you will notice that the `ProcessingContext` will become a parameter throughout virtually all infrastructure
interfaces Axon Framework provides.

It is the replacement of the interfaces with the old `UnitOfWork`, and the spreading of the `ProcessingContext`
instead of the `UnitOfWork` directly, will ensure that operation that are not intended for the end user cannot be
accessed easily anymore.

To conclude, here is a list of changes to take into account concerning the `UnitOfWork`:

1. Operations like `start()`, `commit()`, and `rollback()` are no longer available for the user directly.
2. The nesting functionality of the old `UnitOfWork` through operations like `parent()` and `root()` are completely
   removed.
3. The `UnitOfWork` used to revolve around a `Message`, which is no longer the case for the `ProcessingContext`/
   `ProcessingLifeycle`. Instead, the new approach revolves around a generic action, that may or may not return a
   result.
4. You are no longer tied to the predefined not-started, started, prepare-commit, commit, after-commit, rollback,
   clean-up, and closed phases. Instead, the default phases now are pre-invocation, invocation, post-invocation,
   prepare-commit, commit, and after-commit.
5. The default phases are ordered through the use of an `int`, with space between them to add action before, after, or
   during any phase.
6. The `rollback` logic has been replaced by an on-error, on-complete, and on-finally flow.
   `ProcessingLifecycle#onError` registers an action to be taken on error, while `whenComplete` registers an action to
   performed when after worked as intended. `ProcessingLifecycle#doFinally` registers an operation that is performed on
   success **and** failure of the `ProcessingLifecycle`.
7. Correlation data management, and thus construction of the initial `MetaData` of any `Message`, is removed entirely.
   This is inline with the `UnitOfWork` no longer revolving around a `Message`.
8. The "current" `UnitOfWork` (including the `CurrentUnitOfWork`) is no longer a concept. Instead, all infrastructure
   components will pass along the current context by containing the `ProcessingContext` as a parameter throughout.

Note that the rewrite of the `UnitOfWork` has caused _a lot_ of API changes and numerous removals. For an exhaustive
list of the latter, please check [here](#removed).

## Message

### Payload Type and Qualified Name

The `Message` API has seen roughly two major changes, being:

1. Deprecation and subsequent removal of the `Message#getPayloadType()` operation.
2. Introduction of the `Message#type()` operation, returning a `QualifiedName`.

We omit the `payloadType` from the `Message` as it restricts us in how a Message Handler would read a `Message`.
By making this flexible, Message Handlers can choose how to convert a given `payload` on the spot.

Furthermore, the `payloadType`, as it was a `Class`, tied `Messages` to the fully-qualified-class-name.
Hence, it exposed the internals of an application, making the FQCN part of the domain knowledge.

The latter point can be regarded as undesirable for several reasons, like the fact it exposes technical concerns like
the package structure to others.
Furthermore, it means users are unable to define a so-called "business" or "domain" name to their `Message`; you're
stuck to the FQCN to make things work.
Lastly, it ties Axon Framework into the JVM space, as the FQCN is important to adhere to the `Mesasge#getPayloadType`
method.
Hence, having Axon Framework applications communicate with non-JVM-based applications is, simply put, rough.

It is for this reason that we introduced the `QualifiedName`, which is retrievable through the `Message#type` method.
The `QualifiedName` provides space for a `localName`, a `namespace`, and a `revision`, expecting all three to be present
at all times.
The fields can respectively be used to define the `Class#getSimpleName`, the package name, and the version of the
`Message` it is connected too.
This layer of indirection will allow us to provide the freedom that currently is not an option (as explained above).

## Message Stream

TODO - provide description once the `MessageStream` generics discussion has been finalized.

### Adjusted APIs

TODO - Start filling adjusted operation once the `MessageStream` generics discussion has been finalized.

*

Other API changes
=================

TODO

Moved / Remove Classes
======================

### Moved / Renamed

| Axon 4                                                       | Axon 5                                                       |
|--------------------------------------------------------------|--------------------------------------------------------------|
| org.axonframework.common.caching.EhCache3Adapter             | org.axonframework.common.caching.EhCacheAdapter              |
| org.axonframework.eventsourcing.MultiStreamableMessageSource | org.axonframework.eventhandling.MultiStreamableMessageSource |

### Removed

| Class                                                           | Why                                                                                       |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| org.axonframework.messaging.unitofwork.AbstractUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.BatchingUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.CurrentUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.DefaultUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.ExecutionResult          | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.MessageProcessingContext | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.eventsourcing.eventstore.AbstractEventStore   | Made obsolete through the rewrite of the `EventStore`                                     |
