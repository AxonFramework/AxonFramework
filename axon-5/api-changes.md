Version and Dependency Compatibility
====================================

* Axon Framework is no longer based on JDK 8, but on JDK 21 instead.
* Spring Boot 2 is no longer supported. You should upgrade to Spring Boot 3 or higher.
* Spring Framework 5 is no longer supported. You should upgrade to Spring Framework 6 or higher.
* Javax Persistence is completely replaced for Jakarta Persistence. This means the majority of `javax` reference no
  longer apply.
* EhCache 2 (from group identifier `net.sf.ehcache`) has been faced out entirely in favor of EhCache 3 (from group
  identifier `org.ehcache`).

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
* All annotation logic is moved to the annotation module.
* The Configuration of Axon Framework has been flipped around. Instead of having a `axon-configuration` module that
  depends on all of Axon's modules to provide a global configuration, the core module (`axon-messaging`) of Axon now
  contains a `Configurer` with a base set of operations. This `Configurer` can either take `Components` or `Modules`.
  The former typically represents an infrastructure component (e.g. the `CommandBus`) whereas modules are themselves
  configurers for a specific module of an application. For an exhaustive list of all the operations that have been
  removed, moved, or altered, see the [Configurer and Configuration](#configurer-and-configuration) section.

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

TODO - check if the below section is clear about the uncertainty of the further existence of payloadType (or perhaps
contentType).

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

### Factory Methods, like GenericMessage#asMessage(Object)

The factory methods that would construct a `Mesage` implementation based on a given `Object` have been removed from Axon
Framework.
These factory methods no longer align with the new API, which expects that the `QualifiedName` is set consciously.
Hence, users of the factory methods need to revert to using the constructor of the `Message` implementation instead.
Here's a revised version with improved grammar and clarity:
If a user needs to specify custom `Metadata` for a `Message`, they can use the `Gateway` method overloads that accept
`Metadata` as an additional parameter alongside the payload.

## Message Stream

TODO - provide description once the `MessageStream` generics discussion has been finalized.

### Adjusted APIs

TODO - Start filling adjusted operation once the `MessageStream` generics discussion has been finalized.

## Configurer and Configuration

The configuration API of Axon Framework has seen a big adjustment. You can essentially say it has been turned upside
down. We have done so, because the `axon-configuration` module enforced a dependency on all other modules of Axon
Framework. Due to this, it was, for example, not possible to make an Axon Framework application that only support
command messaging and use the configuration API; the module just pulled in everything.

As an act to clean this up, we have broken down the `Configurer` and `Configuration` into manageable chunks.
As such, the `Configurer` interface now only provides basic operations
to [register components](#registering-components-with-the-componentbuilder-interface), [decorate components](#decorating-components-with-the-componentdecorator-interface), [register enhancers](#registering-enhancers-with-the-configurerenhancer-interface),
and [register modules](#registering-modules-with-the-modulebuilder-interface), besides the basic start-and-shutdown
handler registration from the `LifecycleOperations` interface. The `Configuration` in turn now only has the means to
retrieve components, and it's modules' components.

So, how do you start Axon's configuration? That depends on what you are going to use from Axon Framework. If you, for
example, only want to use the basic messaging concepts, you can start with the `MessagingConfigurer`. You can construct
one through the static `MessagingConfigurer#create` method. This `MessagingConfigurer` will provide you a
couple of defaults, like the `CommandBus` and `QueryBus`. Furthermore, on this configurer, you are able to provide new
or replace existing components, decorate these components, and register the aforementioned module-specific `Modules`.

In this fashion, we intend to ensure the following points:

1. We clean up the `Configurer` and `Configuration` API substantially by splitting it into manageable chunks. This
   should simplify configuration of Axon applications, as well as ease the introduction of specific `Configurer`
   instances like the `MessagingConfigurer`.
2. We reverse the dependency order. In doing so, each Axon Framework module can provide its own `Configurer`. This
   allows users to pick and choose the Axon modules they need.

For more details on how to use the new configuration API, be sure to read the following subsections.

### Registering components with the ComponentBuilder interface

The configuration API boosts a new interface, called the `ComponentBuilder`. The `ComponentBuilder` can generate any
type of component you would need to register with Axon, based on a given `Configuration` instance. By providing the
`Configuration` instance, you are able to pull other (Axon) components out of it that you might require to construct
your component. The `Configurer#registerComponent` method is adjusted to expect such a `ComponentBuilder` upon
registration.

Here's an example of how to register a `DefaultCommandGateway` in Java:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .registerComponent(CommandGateway.class, config -> new DefaultCommandGateway(
                               config.getComponent(CommandBus.class),
                               config.getComponent(MessageTypeResolver.class)
                       ));
    // Further configuration...
}
```

### Decorating components with the ComponentDecorator interface

New functionality to the configuration API, is the ability to provide decorators
for [registered components](#registering-components-with-the-componentbuilder-interface). The decorator pattern is what
Axon Framework uses to construct its infrastructure components, like the `CommandBus`, as of version 5.

In the command bus' example, concepts like intercepting, tracing, being distributed, and retrying, are now decorators
around a `SimpleCommandBus`. We register those through the `Configurer#registerDecorator` method, which expect
provisioning of a `ComponentDecorator` instance. The `ComponentDecorator` provides a `Configuration` and _delegate_
component when invoked, and expects a new instance of the `ComponentDecorator's` generic type to be returned.

Here's an example of how we can decorate the `SimpleCommandBus` in with a `ComponentDecorator`, in Java:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .registerComponent(CommandBus.class, config -> new SimpleCommandBus())
                       .registerDecorator(
                               CommandBus.class,
                               0,
                               (config, delegate) -> new TracingCommandBus(
                                       delegate,
                                       config.getComponent(CommandBusSpanFactory.class)
                               )
                       );
    // Further configuration...
}
```

By providing this functionality on the base `Configurer` interface, you are able to decorate any of Axon's components
with your own custom logic. Since ordering of these decorates can be of importance, you are required to provide an
order upon registration of a `ComponentDecorator`.

### Registering enhancers with the ConfigurerEnhancer interface

The `ConfigurerEnhancer` replaces the old `ConfigurerModule`, with one major difference: A `ConfigurerEnhancer` acts on
the `Configurer` during `Configurer#build` instead of immediately.

This adjustment allows enhancers to enact on its `Configurer` in a pre-definable order. They are thus staged to enhance
when the configuration is ready for it. The order is either the registration order with the `Configurer` or it is based
on the `ConfigurerEnhancer#order` value.

Furthermore, a `ConfigurerEnhancer` can conditionally make adjustments as it sees fit through the
`Configurer#hasComponent` operation. Through this approach, the implementers of an enhancer can choose to
replace a component or decorate a component only when it (or another) is present.

See the example below where decorating a `CommandBus` with tracing logic is only done when a `CommandBus` component is
present:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .registerEnhancer(configurer -> {
                           if (configurer.hasComponent(CommandBus.class)) {
                               configurer.registerDecorator(
                                       CommandBus.class, 0,
                                       (config, delegate) -> new TracingCommandBus(
                                               delegate,
                                               config.getComponent(CommandBusSpanFactory.class)
                                       )
                               );
                           }
                       });
    // Further configuration...
}
```

In the above enhancer, we first validate if there is a `CommandBus` present. Only when that is the case do we choose to
decorate it as a `TracingCommandBus` by retrieving the `CommandBusSpanFactory` from the `Configuration` given to the
`ComponentDecorator`. Note that this sample does expect that somewhere else during the configuration a
`CommandBusSpanFactory` has been added.

### Registering modules with the ModuleBuilder interface

To support clear encapsulation, each `Configurer` provides the means to register a `ModuleBuilder` that constructs a
`Module` based on a `LifecycleSupportingConfiguration`. A `LifecycleSupportingConfiguration` instance is given, so that
the `Module` under construction is able to retrieve components as well as register start and shutdown handlers. The
latter allow the `Module` to take part in the lifecycle of Axon Framework, ensuring that, for example, handler
registration phases happen at the right point in time.

To emphasize it more, the `Module` **is** able to retrieve components from its parent configuration, but this
configuration **is not** able to retrieve components from the `Module`. This allows users to break down their
configuration into separate `Modules` with their own local components. Reusable components would, instead, reside in the
parent configuration.

Imagine you define an integration module in your project that should use a different `CommandBus` from the rest of your
application. By making a `Module` and registering this specific `CommandBus` on this `Module`, you ensure only **it** is
able to retrieve this `CommandBus`. But, if this `Module` requires common components from its parent, it can still
retrieve those. Down below is an example usage of the `SimpleModule` to achieve just that:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .registerModule(config -> new SimpleModule(config)
                               .registerComponent(CommandBus.class, c -> new SimpleCommandBus())
                       );
    // Further configuration...
}
```

### Accessing other Configurer methods from specific Configurer implementations

Although the API of a `Configurer` is greatly simplified, we still believe it valuable to have specific registration
methods guiding the user.
For example, the `Configurer` no longer has a `subscribeCommandBus` operation, as that method does not belong on this
low level API.
However, the specific `MessagingConfigurer` still has this operation, as registering your `CommandBus` on the messaging
layer is intuitive.

To not overencumber users of the `MessagingConfigurer`, we did not give it lifecycle specific configuration operations
like the `AxonApplication#registerLifecyclePhaseTimeout` operation. The same will apply for modelling and event sourcing
configurers: these will not override the registration operations of their delegates.

To be able to access a delegate `Configurer`, you can use the `Configurer#delegate` operation:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .delegate(
                               AxonApplication.class,
                               axonApp -> axonApp.registerLifecyclePhaseTimeout(100, TimeUnit.MILLISECONDS)
                       )
                       .build();
    // Further configuration...
}
```

As specifying the `Configurer` type can become verbose, the `MessagingConfigurer` has a `axon` operation to allow for
the exact same operation:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .axon(axon -> axon.registerLifecyclePhaseTimeout(100, TimeUnit.MILLISECONDS))
                       .build();
    // Further configuration...
}
```

Other API changes
=================

* The `EventBus` has been renamed to `EventSink`, with adjusted APIs. All publish methods now expect a `String context`
  to define in which (bounded-)context an event should be published. Furthermore, either the method holding the
  `ProcessingContext` or the `publish` returning a `CompletableFuture<Void>` should be used, as these make it possible
  to perform the publication asynchronously.

Stored format changes
=====================

## Dead Letters

1. The JPA `org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry` has renamed the `messageType` column to
   `eventType`.
2. The JPA `org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry` has renamed the `type` column to
   `aggregateType`.
3. The JPA `org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry` expects the `QualifiedName` to be
   present under the `type` column, non-nullable.
4. The JDBC `org.axonframework.eventhandling.deadletter.jdbc.DeadLetterSchema` has renamed the `messageType` column to
   `eventType`.
5. The JDBC `org.axonframework.eventhandling.deadletter.jdbc.DeadLetterSchema` has renamed the `type` column to
   `aggregateType`.
6. The JDBC `org.axonframework.eventhandling.deadletter.jdbc.DeadLetterSchema` expects the `QualifiedName` to be present
   under the `type` column, non-nullable.

## Deadlines

1. The JobRunr `org.axonframework.deadline.jobrunr.DeadlineDetails` expects the `QualifiedName` to be present under the
   field `type`.
2. The Quartz `org.axonframework.deadline.quartz.DeadlineJob` expects the QualifiedName to be present in the
   `JobDataMap` under the key `qualifiedType`.
3. The dbscheduler `org.axonframework.deadline.dbscheduler.DbSchedulerBinaryDeadlineDetails` expects the `QualifiedName`
   to be present under the field `t`.
4. The dbscheduler `org.axonframework.deadline.dbscheduler.DbSchedulerHumanReadableDeadlineDetails` expects the
   `QualifiedName` to be present under the field `type`.

Changed Classes
======================

This section contains two tables:

1. A table of all the moved and renamed classes.
2. A table of all the removed classes.

### Moved / Renamed

| Axon 4                                                       | Axon 5                                                       | Module change?                 |
|--------------------------------------------------------------|--------------------------------------------------------------|--------------------------------|
| org.axonframework.common.caching.EhCache3Adapter             | org.axonframework.common.caching.EhCacheAdapter              | No                             |
| org.axonframework.eventsourcing.MultiStreamableMessageSource | org.axonframework.eventhandling.MultiStreamableMessageSource | No                             |
| org.axonframework.eventhandling.EventBus                     | org.axonframework.eventhandling.EventSink                    | No                             |
| org.axonframework.commandhandling.CommandHandler             | org.axonframework.commandhandling.annotation.CommandHandler  | No                             |
| org.axonframework.eventhandling.EventHandler                 | org.axonframework.eventhandling.annotation.EventHandler      | No                             |
| org.axonframework.queryhandling.QueryHandler                 | org.axonframework.queryhandling.annotation.QueryHandler      | No                             |
| org.axonframework.config.Configurer                          | org.axonframework.configuration.Configurer                   | Yes. Moved to `axon-messaging` |
| org.axonframework.config.Configuration                       | org.axonframework.configuration.Configuration                | Yes. Moved to `axon-messaging` |
| org.axonframework.config.Component                           | org.axonframework.configuration.Component                    | Yes. Moved to `axon-messaging` |
| org.axonframework.config.ConfigurerModule                    | org.axonframework.configuration.ConfigurationEnhancer        | Yes. Moved to `axon-messaging` |
| org.axonframework.config.ModuleConfiguration                 | org.axonframework.configuration.Module                       | Yes. Moved to `axon-messaging` |
| org.axonframework.config.LifecycleHandler                    | org.axonframework.configuration.LifecycleHandler             | Yes. Moved to `axon-messaging` |
| org.axonframework.config.LifecycleHandlerInspector           | org.axonframework.configuration.LifecycleHandlerInspector    | Yes. Moved to `axon-messaging` |
| org.axonframework.config.LifecycleOperations                 | org.axonframework.configuration.LifecycleRegistry            | Yes. Moved to `axon-messaging` |

### Removed

| Class                                                           | Why                                                                                       |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| org.axonframework.messaging.unitofwork.AbstractUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.BatchingUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.CurrentUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.DefaultUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.ExecutionResult          | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.MessageProcessingContext | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.eventsourcing.eventstore.AbstractEventStore   | Made obsolete through the rewrite of the `EventStore`.                                    |

Method signature changes
========================

This section contains three subsections, called:

1. [Parameter adjustments](#parameter-adjustments)
2. [Moved methods and constructors](#moved-methods-and-constructors)
3. [Removed methods and constructors](#removed-methods-and-constructors)

### Parameter adjustments

#### Constructors

| Constructor                                                                                | What                           | Why                                          | 
|--------------------------------------------------------------------------------------------|--------------------------------|----------------------------------------------|
| One org.axonframework.messaging.AbstractMessage constructor                                | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| One org.axonframework.serialization.SerializedMessage constructor                          | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.messaging.GenericMessage constructors                      | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.commandhandling.GenericCommandMessage constructors         | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.eventhandling.GenericEventMessage constructors             | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.eventhandling.GenericDomainEventMessage constructors       | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericQueryMessage constructors             | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericSubscriptionQueryMessage constructors | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericStreamingQueryMessage constructors    | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.deadline.GenericDeadlineMessage constructors               | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.messaging.GenericResultMessage constructors                | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.commandhandling.GenericCommandResultMessage constructors   | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericQueryResponseMessage constructors     | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |
| All org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage constructors     | Added the `QualifiedName` type | See [here](#payload-type-and-qualified-name) |

#### Methods

| Method                         | What                                                                   | Why                                     |
|--------------------------------|------------------------------------------------------------------------|-----------------------------------------|
| `Configurer#registerComponent` | Replaced `Function<Configuration, ? extends C>` for `ComponentBuilder` | Added functional interface for clarity. |
| `Configurer#registerModule`    | Replaced `ModuleConfiguration` for `ModuleBuilder`                     | Added functional interface for clarity. |

### Moved/renamed methods and constructors

| Constructor / Method                              | To where                                         |
|---------------------------------------------------|--------------------------------------------------|
| `Configurer#configureCommandBus`                  | `MessagingConfigurer#registerCommandBus`         | 
| `Configurer#configureEventBus`                    | `MessagingConfigurer#registerEventSink`          | 
| `Configurer#configureQueryBus`                    | `MessagingConfigurer#registerQueryBus`           | 
| `Configurer#configureQueryUpdateEmitter`          | `MessagingConfigurer#registerQueryUpdateEmitter` | 
| `ConfigurerModule#configureModule`                | `ConfigurerEnhancer#enhance`                     | 
| `ConfigurerModule#configureLifecyclePhaseTimeout` | `RootConfigurer#registerLifecyclePhaseTimeout`   | 

### Removed methods and constructors

| Constructor / Method                                                            | Why                                                                             | 
|---------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `org.axonframework.config.ModuleConfiguration#initialize(Configuration)`        | Initialize is now replace fully by start and shutdown handlers.                 |
| `org.axonframework.config.ModuleConfiguration#unwrap()`                         | Unwrapping never reached its intended use in AF3 and AF4 and is thus redundant. |
| `org.axonframework.config.ModuleConfiguration#isType(Class<?>)`                 | Only use by `unwrap()` that's also removed.                                     |
| `org.axonframework.config.Configuration#lifecycleRegistry()`                    | A round about way to support life cycle handler registration.                   |
| `org.axonframework.config.Configurer#onInitialize(Consumer<Configuration>)`     | Fully replaced by start and shutdown handler registration.                      |
| `org.axonframework.config.Configurer#defaultComponent(Class<T>, Configuration)` | Each Configurer now has get optional operation replacing this functionality.    |
