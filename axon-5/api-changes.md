API Changes
===========

As is to be expected of a new major release, a lot of things have changed compared to the previous major release. This
document serves the purpose of containing all the changes that may prove breaking to users. Some of the changes have a
lower chance of directly impacting users of Axon Framework 4 (like the [Message Stream](#message-stream)), while others
certainly impact all users (like the [Test Fixture](#test-fixtures) adjustment).

This document can be broken down in five sections:

1. [Version and Dependency Compatibility](#version-and-dependency-compatibility)
2. [Major API Changes](#major-api-changes)
3. [Minor API Changes](#minor-api-changes)
4. [Stored Format Changes](#stored-format-changes)
5. [Class and Method Changes](#class-and-method-changes)

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
* Messages have undergone roughly two major changes. One, they now contain a `MessageType`, decoupling a messages (
  business) type from Java's type system. You can find more details on this [here](#message-type-and-qualified-name).
  Secondly, the `MetaData` of each `Message` now reflects a `Map<String, String>` instead of `Map<String, ?>`, thus
  forcing metadata values to strings. Please read [this](#metadata-with-string-values) section for more details on this
  shift.
* All message-based infrastructure in Axon Framework will return the `MessageStream` interface. The `MessageStream` is
  intended to support empty results, results of one entry, and results of N entries, thus mirroring Event Handlers (no
  results), Command Handlers (one result), and Query Handlers (N results). Added, the `MessageStream` will function as a
  replacement for components like the `DomainEventStream` and `BlockingStream` on the `EventStore`. As such, the
  `MessageStream` changes **a lot** of (public) APIs within Axon Framework. Please check
  the [Message Stream](#message-stream) section for more details, like an exhaustive list of all the adjusted
  interfaces.
* The API of all infrastructure components is rewritten to be "async native." This means that the
  aforementioned [Unit of Work](#unit-of-work) adjustments flow through most APIs, as well as the use of
  a [Message Stream](#message-stream) to provide a way to support imperative and reactive message handlers. See
  the [Adjusted APIs](#adjusted-apis) section for a list of all classes that have undergone changes.
* Axon's `EventStore` implementations let go their aggregate-focus, instead following the "Dynamic Consistency
  Boundary" approach. This shift changed the `EventStore` and `EventStorageEngine` API heavily, providing a lot of
  flexibility in defining how entities are event sourced and how events are appended for them. Although most users won't
  interact with the `EventStore` or `EventStorageEngine` directly, knowing the changes could still prove beneficial. For
  those that are curious, be sure to read the [Event Store](#event-store) section.
* The Configuration of Axon Framework has been flipped around. Instead of having a `axon-configuration` module that
  depends on all of Axon's modules to provide a global configuration, the core module (`axon-messaging`) of Axon now
  contains a `Configurer` with a base set of operations. This `Configurer` can either take `Components` or `Modules`.
  The former typically represents an infrastructure component (e.g. the `CommandBus`) whereas modules are themselves
  configurers for a specific module of an application. For an exhaustive list of all the operations that have been
  removed, moved, or altered, see the [Configurer and Configuration](#applicationconfigurer-and-configuration) section.
* The Test Fixtures have been replaced by an approach that, instead of an Aggregate or Saga class, take in an
  `ApplicationConfigurer` instance. In doing so, test fixtures reflect the actual application configuration. This
  resolves the predicament that you need to configure your application twice (for production and testing), making the
  chance slimmer that parts will be skipped. For more on this change, please check the [Test Fixtures](#test-fixtures)
  section of this document.
* The annotation logic of all modules is moved to a separate `annotation` package.
* All reflection logic is moved to a dedicated "reflection" package per module.
* Aggregates are now referred to as Entities, as the Dynamic Consistency Boundary allows for more fluid boundaries
  around entities.
  In addition, entities have been redesigned to make them more flexible, allowing for immutable
  entities, declarative modeling, and a more fluent API. For more on this, check the
  [Aggregates to Entities](#aggregates-to-entities) section.

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
the use of the `ThreadLocal`. As such, you will notice that the `ProcessingContext` will become a parameter throughout
virtually **all** infrastructure interfaces Axon Framework provides. This will become most apparent on all message
handlers.

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
list of the latter, please check [here](#removed-classes).

## Legacy components

During the development of Axon Framework 5, we have decided that some features move to the legacy package, such as
Sagas. These are features that we think should be either removed, or that deserve a big overhaul in a future version.
Meanwhile, users can thus use the legacy package to continue using these features, while we can focus on the new
features and improvements in Axon Framework 5.

However, even these legacy components have seen some changes. The most notable one is that most of these components
require a `ProcessingContext` to be passed in. This is to ensure good cooperation between old and new parts of the
framework. This means that some changes might be necessary in your code, such as passing in the
`ProcessingContext` to the `InterceptorChain`:

```java
public class MyInterceptingEventHandler {

    @MessageHandlerInterceptor
    public void handle(MyEvent event, InterceptorChain chain, ProcessingContext context) {
        chain.proceedSync(context);
    }
}
```

You are able inject the `ProcessingContext` in any message-handling method, so this is always available. Any code that uses the old `UnitOfWork` should be rewritten to put resources in this context.

We will provide a migration guide, as well as OpenWrite recipes for these scenarios.

## Message

### Message Type and Qualified Name

For added flexibility with Axon Framework's `Message` API, we introduced two classes, namely:

1. The `MessageType`, and
2. the `QualifiedName`.

The `MessageType` is a combination of a `QualifiedName` and a version (of type `String`). **Every** `Message`
implementation now has the `type()` method, returning its `MessageType`. The intent for this new class on the `Message`,
is to ensure all messages clarify their version and qualified name within the domain they act in. Note that both the
`QualifiedName` and version are non-null variables on the `MessageType`, ensuring they are always present.

This is a shift compared to Axon Framework 4 in roughly two areas, being:

1. The `version` (`revision` as it was called in AF4) is no longer an event-only thing. This makes it so that
   applications can describe the version of their commands and queries more easily, making it possible to construct
   converters or define default mappings between different application releases.
2. The introduction of the `QualifiedName` makes it so that Axon Framework does not have to rely on the
   `Message#getPayloadType` anymore as the defining factor of the `Message` in question.

Thus, through the introduction of the `QualifiedName`, users are able to decouple their message class implementations
from their definition within the application. For example, somebody can define business names for their messages, easing
and clarifying communication with the business and the developer. Or, users can create several unique message
implementations per (micro)service that all map to the same `QualifiedName`. The latter argument makes it so that users
don't have to rely on sharing their concrete message implementations between parties.

Next to adding the `MessageType` to the `Message`, this shift also introduced the dependency on a `QualifiedName` for
message handlers. This shift came from a similar desire as with the `Message`: to ensure somebody doesn't have to rely
on the FQCN and its implementation. On top of this, it allows Axon Framework to deal with messages that come outside the
JVM space more easily.

Although throughout Axon Framework now anticipates the `MessageType` on `Messages` and the `QualifiedName` when
subscribing message handlers, this does not change the default behavior: if you don't specify anything,
the framework will use the `Class#getName` to define the `QualifiedName`, and thus subsequently to define a
`MessageType`. This shift should make it feasible for those to stick to the old behavior or to decouple their concrete
classes and message from one another.

### Factory Methods, like GenericMessage#asMessage(Object)

The factory methods that would construct a `Mesage` implementation based on a given `Object` have been removed from Axon
Framework. These factory methods no longer align with the new API, which expects that the `MessageType` is set
consciously. Hence,
users of the factory methods need to revert to using the constructor of the `Message` implementation instead.

## Message Stream

We have introduced the so-called `MessageStream` to allow people to draft both imperative **and** reactive message
handlers. As such, the `MessageStream` is the expected result type from event handlers, command handlers, and query
handlers. Furthermore, the `MessageStream` can mirror response of nothing (zero), one, or N, thus reflecting the
expected behavior of an event handler (no response), a command handler (one response), and query handlers (N responses).
Besides being **the** response for all message handlers in Axon Framework, it is also the return type when
streaming and sourcing events from an `EventStore`.

To achieve all this, the `MessageStream` has several creational methods, like:

1. `MessageStream#fromIterable`
2. `MessageStream#fromStream`
3. `MessageStream#fromFlux`
4. `MessageStream#fromFuture`
5. `MessageStream#just`
6. `MessageStream#empty`

As can be expected, the `MessageStream` streams implementation of `Message`. Hence, the creational methods expect
`Message` implementations when invoked. On top of that, you can add context-specific information to each entry in the
`MessageStream`, by specifying a lambda that takes in the `Message` and returns a `Context` object. For example, Axon
Framework uses this `Context` to add the aggregate identifier, aggregate type, and sequence number for events that
originate from an aggregate-based event store (thus a pre-Dynamic Consistency Boundary event store).

### MetaData with String values

The `MetaData` class in Axon Framework changed its implementation. Originally, it was a `Map<String, ?>` implementation.
As of Axon Framework 5, it is a `Map<String, String>`.

The reason for this shift can be broken down in three main pillars:

1. It greatly simplifies de-/serialization for storing `Messages` and putting `Messages` over the wire, since any value
   is a `String` in all cases.
2. It aligns better with how other services, libraries, and frameworks view metadata, which tends to be a `String` or
   byte array.
3. Depending on application requirements, the de-/serialization of specific values can be different. By enforcing a
   `String`, we streamline the process.

Although this may seem like a devolution of the `Message`, we believe this stricter guardrails will help all users in
the long run.

## Adjusted APIs

The changes incurred by the new [Unit of Work](#unit-of-work) and [Message Stream](#message-stream) combined form the
basis to make Axon Framework what we have dubbed "Async Native." In other words, it is intended to make Axon Framework
fully asynchronous, top to bottom, without requiring people to deal with asynchronous programming details (e.g.
`CompletableFuture` / `Mono`) at each and every turn.

This shift has an obvious impact on the API of Axon Framework's infrastructure components. The APIs now favor the use of
the `ProcessingContext`, `MessageStream`, and are generally made asynchronous through the use of a `CompletableFuture`.
As these APIs are in most cases not directly invoked by the user, they should typically not form an obstruction.
Nonetheless, if you **do** use these operations, it is good to know they've changed with the desire to be async native.

The following classes have undergone changes to accompany this shift:

* The `EventStorageEngine`
* The `EventStore`
* The `Repository`
* The `StreamableMessageSource`
* The `CommandBus`

## Event Store

The `EventStore` has seen a rigorous change in Axon Framework 5 to accompany the Dynamic Consistency Boundary.

The Dynamic Consistency Boundary, or DCB for short, allows for a flexible boundary to what should be appended
consistently with other existing event streams in the event store. In doing so, it eliminates the focus on the
aggregate identifier, replacing it for user defined "tags." Note that tags are plural. As such, an event is no longer
either attached to zero or one aggregate/entity, but potentially several.

This shift will provide greater flexibility in deriving models, as there is no longer a hard boundary around the
aggregate stream. It allows users to depend on N-"aggregate" streams in one sourcing operation, allowing commands to
span a more complete view.

To not overencumber the sourcing operation, not only tags, but also (event) "types" are used during event store
operation. The types act as a filter on the entity streams that matching the tags. The tags and the types combined from
the `EventCriteria`. It is this `EventCriteria` that Axon Framework uses
for appending events, sourcing events, and streaming events.

It is the `EventCriteria` that thus allows you to define "slices" of an otherwise potentially large aggregate model.
Events that (although part of the entity's stream) don't influence the decision-making process, can be omitted when
sourcing an entity.

As becomes apparent, this is a rather massive changes for those interacting directly with the `EventStore` API from Axon
Framework. Luckily, most users will not interact with this infrastructure component directly. Although this shift
removes the aggregate focus entirely, it does not remove the option to use aggregates. It is purely the internals of
appending, sourcing, and streaming that shift from a 0-or-1 event stream focus to a 0-N event stream solution.

### Appending Events

In the past, you would use the `EventStore#publish` operation to publish events. To ensure the event would be part of an
aggregate stream, users would deal with the `AggregateLifecycle#apply` operation. This used, internally, a `ThreadLocal`
to find the "active" aggregate model, providing the `apply` operation knowledge about the aggregate identifier and
sequence number.

To append events in Axon Framework 5, users first need to start an `EventStoreTransaction` with an active
`ProcessingContext` (see [Unit of Work](#unit-of-work) for more on the `ProcessingContext`).
From there, to append, you would use the `EventStoreTransaction#appendEvent(EventMessage)` operation. To make it so that
appending events are part of an aggregate / consistency boundary that's active, users would first invoke
`EventStoreTransaction#source(SourcingCondition)` (as further explained [here](#sourcing-events)). It is the act of
sourcing that instructs Axon Framework to make a matching `AppendCondition` to use during appending events.

In code, this would like so:

```java
public void appendEvents(EventStore eventStore,
                         ProcessingContext context,
                         EventMessage<?> event) {
    EventStoreTransaction transaction = eventStore.transaction(context);
    transaction.appendEvent(event);
}
```

As stated in [Unit of Work](#unit-of-work), the `ProcessingContext` is propagated throughout Axon Framework. As such, it
is **always** available in message handling functions.

Note that above is the technical solution, applicable only to those interacting with the `EventStore` directly. To
publish events as part of an entity, an `EventAppender` can be injected in command handling methods. On an
`@CommandHandler` annotated method, this would look as follows:

```java

@CommandHandler
public void handle(SubscribeStudentCommand command,
                   EventAppender appender) {
    StudentSubscribedEvent event = this.decide(command);
    appender.append(event);
}
```

### Sourcing Events

In the past, to source an aggregate, the `EventStore#readEvents(String aggregateIdentifier)` method or
`EventStore#readEvents(String aggregateIdentifier, Long firstSequenceNumber)` method was used. Since events are no
longer attached to a single aggregate, neither exist as is anymore.

Instead, the `EventStoreTransaction`, that is also used for [appending events](#appending-events), should be used to
source an entity. More specifically, the `EventStoreTransaction#source(SourcingCondition)` method should be invoked. The
`SourcingCondition` in turn contains the `EventCriteria` to source for, as well as that it is able to define a start and
end position.

If you want to source an (old-fashioned) aggregate, the `EventCriteria` contains a single `Tag` of key `aggregateId` and
a value matching the aggregate to source. In code, this would look as follows:

```java
public void sourcingEvents(EventStore eventStore,
                           ProcessingContext context) {
    Tag aggregateIdTag = new Tag("aggregateId", UUID.randomUUID().toString());
    EventCriteria criteria = EventCriteria.havingTags(aggregateIdTag);
    SourcingCondition sourcingCondition = SourcingCondition.conditionFor(criteria);

    EventStoreTransaction transaction = eventStore.transaction(context);
    MessageStream<? extends EventMessage<?>> sourcedEvents = transaction.source(sourcingCondition);
    // Process the sourced events as desired...
}
```

Note that we do not expect users to source an aggregate / entity manually like this. Axon Framework has extensive
support to define both state-based and event-sourced entities, ensuring all components are in place such that you
*never* have to create any form of condition.

### Streaming Events

In the past, to stream events, the `StreamableMessageSource#openStream(TrackingToken)` method (which the `EventStore`
implements) would be used. This behavior shifted to align with a DCB-based event store. This means we now expect a
condition with an `EventCriteria`, referring to several tags and types. For streaming events, the most feasible filter
are the types, as event streaming is intended to create query models.

To stream events, Axon Framework 5 has replaced the `StreamableMessageSource` for a `StreamableEventSource`.
Furthermore, the `open` operation no longer expects a `TrackingToken`, but a `StreamingCondition` instead. When invoked
manually (thus without the use of Event Processors), this would look as such:

```java
public void streamingEvents(
        StreamableEventSource<EventMessage<?>> streamableEventSource
) throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<TrackingToken> asyncToken = streamableEventSource.headToken();
    TrackingToken trackingToken = asyncToken.get(500, TimeUnit.MILLISECONDS);
    StreamingCondition streamingCondition = StreamingCondition.startingFrom(trackingToken);

    MessageStream<EventMessage<?>> eventStream = streamableEventSource.open(streamingCondition);
    // Process the event stream as desired...
}
```

## ApplicationConfigurer and Configuration

The configuration API of Axon Framework has seen a big adjustment. You can essentially say it has been turned upside
down. We have done so, because the `axon-configuration` module enforced a dependency on all other modules of Axon
Framework. Due to this, it was, for example, not possible to make an Axon Framework application that only supports
command messaging and use the configuration API; the module just pulled in everything.

As an act to clean this up, we have broken down the `Configurer` and `Configuration` into manageable chunks.
As such, the (new) `ApplicationConfigurer` interface now only provides basic operations
to [register components](#registering-components-with-the-componentbuilder-interface), [decorate components](#decorating-components-with-the-componentdecorator-interface), [register enhancers](#registering-enhancers-with-the-configurationenhancer-interface), [register modules](#registering-modules-through-the-modulebuilder-interface),
and [register factories](#registering-component-factories), besides the basic [start-and-shutdown
handler registration](#component-lifecycle-management). It does this by having two different registries, being the
`ComponentRegistry` and `LifecycleRegistry`. The former takes care of the component, decorator, enhancer, and module
registration. The latter provides the aforementioned methods to register start and shutdown handlers as part of
registering components. The `Configuration` in turn now only has the means to retrieve components (optionally), and it's
modules' components. This means **all** infra-specific methods, like for example `Configuration#eventBus`, no longer
exist.

So, how do you start Axon's configuration? That depends on what you are going to use from Axon Framework. If you, for
example, only want to use the basic messaging concepts, you can start with the `MessagingConfigurer`. You can construct
one through the static `MessagingConfigurer#create` method. This `MessagingConfigurer` will provide you a
couple of defaults, like the `CommandBus` and `QueryBus`. Furthermore, on this configurer, you are able to provide new
or replace existing components, decorate these components, and register the aforementioned module-specific `Modules`.
Subsequently, if you want to do event sourcing with Axon Framework, you would start by invoking the
`EventSourcingConfigurer#create` operation

Each of these layers provides registration methods that are specific for the layer. Henceforth, the
`MessagingConfigurer` has a `registerCommandBus`, `registerEventSink`, and `registerQueryBus` method. Subsequently, the
`EventSourcingConfigurer` has the `registerEventStore` and `registerEventStorageEngine` method. To be able to reach the
lower level operations, each `ApplicationConfigurer` wraps a more low-level variant. This causes the "layering" we
talked about earlier. The `EventSourcingConfigurer` thus wraps a `ModellingConfigurer`, the `ModellingConfigurer` a
`MessagingConfigurer`, and the `MessagingConfigurer` contains the `ComponentRegistry` and `LifecycleRegistry`
components. You can move down each of these layers to access gradually lower-level APIs. For more details on this, read
the following [section](#accessing-lower-level-applicationconfigurer-methods).

In this fashion, we intend to ensure the following points:

1. We clean up the (old) `Configurer` and `Configuration` API substantially by splitting it into manageable chunks. This
   should simplify configuration of Axon applications, as well as ease the introduction of specific
   `ApplicationConfigurer` instances like the `MessagingConfigurer`.
2. We reverse the dependency order. In doing so, each Axon Framework module can provide its own `Configurer`. This
   allows users to pick and choose the Axon modules they need.

For more details on how to use the new configuration API, be sure to read the following subsections.

### Registering components with the ComponentBuilder interface

The configuration API boosts a new interface, called the `ComponentBuilder`. The `ComponentBuilder` can generate any
type of component you would need to register with Axon, based on a given `Configuration` instance. By providing the
`Configuration` instance, you are able to pull other (Axon) components out of it that you might require to construct
your component. The `ComponentRegistry#registerComponent` method is adjusted to expect such a `ComponentBuilder` upon
registration.

Here's an example of how to register a `DefaultCommandGateway` through the `registerComponent` method:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .componentRegistry(registry -> registry.registerComponent(
                               CommandGateway.class,
                               config -> new DefaultCommandGateway(
                                       config.getComponent(CommandBus.class),
                                       config.getComponent(MessageTypeResolver.class)
                               )
                       ));
    // Further configuration...
}
```

Although the sample above uses the `MessagingConfigurer#componentRegistry(Consumer<ComponentRegistry>)` operation, the
same `ComponentBuilder` behavior resides on higher-level operations like `MessagingConfigurer#registerCommandBus`.

### Component Lifecycle Management

As part of any application configuration, there are certain tasks that should be completed on start-up or shutdown. Axon
Framework provided a space for this in three ways, being:

1. On the `Configurer` while registering components.
2. By implementing `Lifecycle` on the component.
3. By adding `@StartHandler` and `@ShutdownHandler` annotated methods to the component.

Since Axon Framework 5, the `Lifecycle` interface and `@StartHandler` and `@ShutdownHandler` annotations no longer
**exist**.

We have done so, because the interface and annotation approach **require** an instance of the component to correctly
invoke the register lifecycle handler operation. This requires eager initialization of components, as otherwise the
methods cannot be accessed. This breaks the desire that defaults given by Axon Framework are not constructed when they
are not used. On top of that, the annotations enforced reflection on all registered components, something we are
steering away from as core component of Axon Framework (as it should be a choice of the user).

Instead, we chose to stick to option one, as this allows for lazy initialization of the components. However, it still
slightly differs from Axon Framework 4. Let us provide an example of registering start and shutdown handlers, for
components **and** decorators:

```java
public static void main(String[] args) {
    EventSourcingConfigurer.create()
                           .componentRegistry(registry -> registry.registerComponent(
                                   ComponentDefinition.ofType(AxonServerConnectionManager.class)
                                                      .withInstance(AxonServerConnectionManager.builder()
                                                                                               /* left out for brevity*/
                                                                                               .build())
                                                      .onStart(
                                                              Phase.INSTRUCTION_COMPONENTS,
                                                              AxonServerConnectionManager::start
                                                      )
                           ))
                           .componentRegistry(registry -> registry.registerDecorator(
                                   DecoratorDefinition.forType(DeadlineManager.class)
                                                      .with((config, name, delegate) -> /* left out for brevity*/)
                                                      .onShutdown(
                                                              Phase.INBOUND_EVENT_CONNECTORS,
                                                              DeadlineManager::shutdown
                                                      )
                           ));
}
```

As shown in the example above, instead of directly registering the component or decorator, the so-called
`ComponentDefinition` and `DecoratorDefinition` are used. These definitions allow you to describe the full extent of how
the component/decorator should behave. Thus including any start or shutdown handlers that should be invoked. In this
example, a definition is created for an `AxonServerConnectionManager` that should start in the `INSTRUCTION_COMPONENTS`.
Furthermore, a decorator definition is given for all components of type `DeadlineManager`, that should be shutdown in
the `INBOUND_EVENT_CONNECTORS`.

This registration approach of a complete definition, wherein the construction of the component and the decoration
thereof are kept and **only** invoked when used in your end application, ensures that lifecycle management does not
cause eager initialization of _any_ component. 

### Decorating components with the ComponentDecorator interface

New functionality to the configuration API, is the ability to provide decorators
for [registered components](#registering-components-with-the-componentbuilder-interface). The decorator pattern is what
Axon Framework uses to construct its infrastructure components, like the `CommandBus`, as of version 5.

In the command bus' example, concepts like intercepting, tracing, being distributed, and retrying, are now decorators
around a `SimpleCommandBus`. We register those through the `ComponentRegistry#registerDecorator` method, which expects
provisioning of a `ComponentDecorator` instance. The `ComponentDecorator` provides a `Configuration`, name, and
_delegate_ component when invoked, and expects a new instance of the `ComponentDecorator's` generic type to be returned.

Here's an example of how we can decorate the `SimpleCommandBus` in with a `ComponentDecorator`, in Java:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .componentRegistry(registry -> registry.registerComponent(
                               CommandBus.class, config -> new SimpleCommandBus()
                       ))
                       .componentRegistry(registry -> registry.registerDecorator(
                               CommandBus.class,
                               0,
                               (config, name, delegate) -> new TracingCommandBus(
                                       delegate,
                                       config.getComponent(CommandBusSpanFactory.class)
                               )
                       ));
    // Further configuration...
}
```

By providing this functionality on the `ComponentRegistry`, you are able to decorate any of Axon's components
with your own custom logic. Since ordering of these decorates can be of importance, you are required to provide an
order upon registration of a `ComponentDecorator`.

### Registering enhancers with the ConfigurationEnhancer interface

The `ConfigurationEnhancer` replaces the old `ConfigurerModule`, with one major difference: A `ConfigurationEnhancer`
acts on the `ComponentRegistry` during `ApplicationConfigurer#build` instead of immediately.

This adjustment allows enhancers to enact on its `ComponentRegistry` in a pre-definable order. They are thus staged to
enhance when the configuration is ready for it. The order is either the registration order with the `ComponentRegistry`
or it is based on the `ConfigurationEnhancer#order` value.

Furthermore, a `ConfigurationEnhancer` can conditionally make adjustments as it sees fit through the
`ComponentRegistry#hasComponent` operation. Through this approach, the implementers of an enhancer can choose to replace
a component or decorate a component only when it (or another) is present.

See the example below where decorating a `CommandBus` with tracing logic is only done when a `CommandBus` component is
present:

```java
public static void main(String[] args) {
    MessagingConfigurer.create()
                       .componentRegistry(registry -> registry.registerEnhancer(configurer -> {
                           if (configurer.hasComponent(CommandBus.class)) {
                               configurer.registerDecorator(
                                       CommandBus.class, 0,
                                       (config, name, delegate) -> new TracingCommandBus(
                                               delegate,
                                               config.getComponent(CommandBusSpanFactory.class)
                                       )
                               );
                           }
                       }));
    // Further configuration...
}
```

In the above enhancer, we first validate if there is a `CommandBus` present. Only when that is the case do we choose to
decorate it as a `TracingCommandBus` by retrieving the `CommandBusSpanFactory` from the `Configuration` given to the
`ComponentDecorator`. Note that this sample does expect that somewhere else during the configuration a
`CommandBusSpanFactory` has been added.

### Registering Modules through the ModuleBuilder interface

To support clear encapsulation, each `ApplicationConfigurer` provides the means to register a `ModuleBuilder` that
constructs a `Module`. A `Module` is basically a container of a `ComponentRegistry` with a parent `ComponentRegistry`.
This structure ensures that (1) it has its own local registry that others cannot influence and (2) that it is still able
to retrieve components from the parent registry.

To emphasize it more, the `Module` **is** able to retrieve components from its parent configuration, but this
configuration **is not** able to retrieve components from the `Module`. This allows users to break down their
configuration into separate `Modules` with their own local components. Reusable components would, instead, reside in the
parent configuration.

Imagine you define an integration module in your project that should use a different `CommandBus` from the rest of your
application. By making a `Module` and registering this specific `CommandBus` on this `Module`, you ensure only **it** is
able to retrieve this `CommandBus`. But, if this `Module` requires common components from its parent, it can still
retrieve those.

Besides the exemplified infrastructure separation from above, Axon Framework uses these `Modules` to encapsulate message
handling. A concrete example of this, is the `StatefulCommandHandlingModule` (that can be registered with the
`ModellingConfigurer`). We have made this decision to strengthen the guideline that your message handlers "should not be
aware of, nor make any assumptions of other components." This rule comes from the location transparency definition,
which Axon Framework provides through it's messaging support. By having the `Module` encapsulated from the rest, we
ensure the parent `ApplicationConfigurer`, nor other `Modules`, are able to depend on it.

Down below is shortened example on how to register a `StatefulCommandHandlingModule`:

```java
public static void main(String[] args) {
    ModellingConfigurer.create()
                       .registerStatefulCommandHandlingModule(
                               StatefulCommandHandlingModule.named("my-module")
                               // Further MODULE configuration...
                       );
    // Further configuration...
}
```

### Registering Component Factories

The new `ComponentFactory` interface allows us, and users, to provide a component factory for components. This provides
a mechanism to, for example, construct a factory that can construct context-specific `CommandGateway` instances or
`EventStorageEngines`. Whenever a `ComponentFactory` constructs an instance, it will register it with the
`Configuration` for future reference. This ensures that when you request a component several times from the
`Configuration` that the same instance will be returned. Note that a `ComponentFactory` may decide against constructing
a component if (1) the `name` is not of the desired format or (2) if the `Configuration` does not contain the required
components to construct an instance.

Axon Framework uses the `ComponentFactory` to, for example, register an `AxonServerEventStorageEngineFactory`. This
`ComponentFactory` for the `AxonServerEventStorageEngine` can construct context-specific `AxonServerEventStorageEngine`
instances. To that end, it expects the `name` to comply to the following format: `"storageEngine@{context-name}"`.

A registered factory is consulted **only** when the `ComponentRegistry` does not contain a component for the
type-and-name combination. Hence, if the `ComponentRegistry` has a `CommandGateway` component registered with it **and**
there is a `ComponentFactory<CommandGateway>` present on the registry, the factory will not be invoked.

Down below is an example when a factory is **not** invoked:

```java
public static void main(String[] args) {
    AxonConfiguration configuration =
            MessagingConfigurer.create()
                               .componentRegistry(registry -> registry.registerComponent(
                                       CommandGateway.class,
                                       config -> new DefaultCommandGateway(
                                               config.getComponent(CommandBus.class),
                                               config.getComponent(MessageTypeResolver.class)
                                       )
                               ))
                               .componentRegistry(registry -> registry.registerFactory(new CommandGatewayFactory()))
                               // Further configuration...
                               .build();

    // This will invoke the CommandGatewayFactory!
    CommandGateway commandGateway = configuration.getComponent(CommandGateway.class, "some-context");
}
```

However, if we take the above example and invoke `getComponent` with a different `name`, the factory will be invoked:

```java
public static void main(String[] args) {
    AxonConfiguration configuration =
            MessagingConfigurer.create()
                               .componentRegistry(registry -> registry.registerComponent(
                                       CommandGateway.class,
                                       config -> new DefaultCommandGateway(
                                               config.getComponent(CommandBus.class),
                                               config.getComponent(MessageTypeResolver.class)
                                       )
                               ))
                               .componentRegistry(registry -> registry.registerFactory(new CommandGatewayFactory()))
                               // Further configuration...
                               .build();

    // This will return the registered DefaultCommandGateway!
    CommandGateway commandGateway = configuration.getComponent(CommandGateway.class);
}
```

### Accessing lower-level ApplicationConfigurer methods

Although the API of an `ApplicationConfigurer` is greatly simplified, we still believe it valuable to have specific
registration methods guiding the user. For example, the `ApplicationConfigurer` no longer has a `subscribeCommandBus`
operation, as that method does not belong on this low level API. However, the specific `MessagingConfigurer` still has
this operation, as registering your `CommandBus` on the messaging layer is intuitive.

To not overencumber users of the `MessagingConfigurer`, we did not give it lifecycle specific configuration operations
like the `LifecycleRegistry#registerLifecyclePhaseTimeout` operation. The same applies for modelling and event sourcing
configurers: these will not override the registration operations of their delegates.

To be able to access a "delegate" `ApplicationConfigurer` there are special accessor methods that expect a lambda of the
delegate to be given. For example the `MessagingConfigurer` has a `componentRegistry(Consumer<ComponentRegistry>)` and
`lifecycleRegistry(Consumer<LifecycleRegistry>)` operation to invoke operations on the `ComponentRegistry` and
`LifecycleRegistry` respectively. Furthermore, the `ModellingConfigurer` has the
`messaging(Consumer<MessagingConfigurer>)` operation to move up to the delegate `MessagingConfigurer` layer:

```java
public static void main(String[] args) {
    ModellingConfigurer.create()
                       .componentRegistry(componentRegistry -> componentRegistry.registerComponent(
                               CommandGateway.class,
                               config -> new DefaultCommandGateway(
                                       config.getComponent(CommandBus.class),
                                       config.getComponent(MessageTypeResolver.class)
                               )
                       ))
                       .lifecycleRegistry(lifecycleRegistry -> lifecycleRegistry.registerLifecyclePhaseTimeout(
                               5, TimeUnit.DAYS
                       ))
                       .messaging(messagingConfigurer -> messagingConfigurer.registerEventSink(
                               config -> new CustomEventSink()
                       ));
    // Further configuration...
}
```

## Aggregates to Entities

Axon Framework 5 elevates the concept of Entities to the top level, as aggregate no longer accurately
describes the concept. With the introduction of [DCB](#event-store), more fluid boundaries of entities are possible.

This section has been written in a way that is easy to follow if you read the sections in order. However, if you
are already familiar with the changes, you can jump to the relevant section using the links below:

- [Aggregates are now referred to as Entities](#aggregates-are-now-entities).
- [Entities can now be defined declaratively, instead of only through reflection.](#declarative-modeling-first).
- [Entities can be immutable, allowing for Java records and Kotlin data classes.](#immutable-entities).
- [Entity constructors can take in the first event as a payload or `EventMessage`, allowing for non-nullable
  fields.](#entity-constructor-changes)
- [Constructor command handlers are gone, and a creational command is a static method on the entity class.](#creational-command-handlers)
- [Reflection-based entities have gained some new capabilities](#reflection-based-entities)

### Aggregates are now Entities

In Axon Framework 5, the concept of aggregates has been replaced with entities. This change reflects the shift from
a strict aggregate boundary to a more flexible entity boundary, allowing for a more fluid definition of entities
that can span multiple event streams. The term "aggregate" is no longer used in the API, and all references to
aggregates have been replaced with "entities."

### Declarative modeling first

When handling messaging for an entity, the framework needs to know which commands and events can be handled
by the entity and which child entities it has. This is what we call the 'EntityMetamodel.'

While aggregates worked only through reflection before, with the Axon Framework 5' entities this can be declaratively
defined.
You can start defining a metamodel by calling `EntityMetamodel.forEntityType(entityType)` and declare command
handlers, event handlers, and
child entities. If you have a polymorphic entity, one that has multiple concrete types and extends one supertype,
you can use `EntityMetamodel.forPolymorphicEntityType(entityType)` to define the entity metamodel.

```java
EntityMetamodel<ImmutableTask> metamodel = EntityMetamodel
        .forEntityType(ImmutableTask.class)
        .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableTask.class))
        .instanceCommandHandler(commandQualifiedName, (command, entity, context) -> {
            // Handle the command
            return MessageStream.empty().cast();
        })
        .addChild(/* child entity definition */)
        .build();
```

However, the use of reflection is still possible. The `AnnotatedEntityMetamodel` reads the entity information
in a way that is similar to Axon Framework 4, and creates a delegate `EntityMetamodel` of the right type, with
the right handlers. This means that the entity structure is clearly defined and debuggable,
and less reflection is needed at runtime, which improves performance.

```java
EntityMetamodel<ImmutableTask> metamodel = AnnotatedEntityMetamodel.forConcreteType(
        ImmutableTask.class,
        configuration.getComponent(ParameterResolverFactory.class),
        configuration.getComponent(MessageTypeResolver.class)
);
```

### Immutable entities

Event-sourced entities can now be created in an immutable fashion, which wasn't possible before Axon Framework 5.
This allows you to create entities out of Java records or Kotlin data classes:

```java
record MyEntity(
        String id,
        String name
) {

    @EventSourcingHandler
    public MyEntity on(MyEntityNameChangedEvent event) {
        return new MyEntity(id, event.getNewName());
    }
}
```

Or, in Kotlin:

```kotlin
data class MyEntity(
    val id: String,
    val name: String
) {
    @EventSourcingHandler
    fun on(event: MyEntityNameChangedEvent): MyEntity {
        return copy(name = event.newName)
    }
}
```

By returning a new instance of the entity in the event sourcing handler, you can evolve the state of the entity
without mutating the original instance. This is particularly useful in functional programming paradigms and allows for
better immutability guarantees in your code. This works with both Java records and Kotlin data classes, as well as
traditional classes.

This is made possible because the first command is handled by a static method, not a constructor, and is responsible for
verifying the command and creating the entity. These static methods
are [creational command handler](#creational-command-handlers). Once the first event is published, the entity is
created using the constructor defining the payload or `EventMessage`. Commands after this will be handled by methods on
the instance of the entity.

To evolve, or change the state, of an entity, `@EventSourcingHandlers` or `EntityEvolvers` can return a new instance of
the entity based on an event. This entity will then be used for the next command or next event.

### Entity Constructor changes

The world is moving to non-nullability guarantees, and for good reason. However, aggregates required a no-arg
constructor to be able to instantiate the aggregate. This meant that fields could not be non-nullable, as the
constructor would not be able to set them. In Axon Framework 5, this has changed.

This is how a kotlin class would traditionally look:

```kotlin
class MyPreFiveClass {
    // Kotlin classes have inherently a no-arg constructor

    @AggregateIdentifier
    private lateinit var id: String

    @CommandHandler
    fun handle(command: CreateMyEntityCommand) {
        apply(MyEntityCreatedEvent(command.id, command.name))
        // Other initialization logic...
    }

    @EventSourcingHandler
    fun on(event: MyEntityCreatedEvent) {
        this.id = event.id
        // Other initialization logic...
    }
}
```

As you can see, the `lateinit var` makes the `id` field non-nullable, but it can throw if not set when accessed.
In addition, you can never make it a `val`, so it remains mutable.
Java had similar limitations, but it was simply not as visible as it is in Kotlin:

```java
public class MyPreFiveClass {

    private MyPreFiveClass() {
        // No-arg constructor required for Axon Framework 4
    }

    @AggregateIdentifier
    private String id;

    @CommandHandler
    public void handle(CreateMyEntityCommand command) {
        apply(new MyEntityCreatedEvent(command.getId(), command.getName()));
        // Other initialization logic...
    }

    @EventSourcingHandler
    public void on(MyEntityCreatedEvent event) {
        // this.id is null here
        this.id = event.getId();
        // Other initialization logic...
    }
}
```

From Axon Framework 5 onwards, the constructor of an entity can take in the first event as a payload or `EventMessage`.
This allows you to set the fields of the entity in a non-nullable way,
and it allows you to make them `val` in Kotlin or `final` in Java.
This is what the code would look like in Kotlin:

```kotlin
data class MyEntity(
    val id: String,
    val name: String
) {
    @EntityCreator
    constructor(event: MyEntityCreatedEvent) : this(
        id = event.id,
        name = event.name
    )

    companion object {
        @CommandHandler
        fun create(command: CreateMyEntityCommand, appender: EventAppender) {
            EventAppender.append(MyEntityCreatedEvent(command.id, command.name))
        }
    }
}
```

And this is what it would look like in Java:

```java
public class MyEntity {

    @AggregateIdentifier
    private final String id;
    private final String name;

    @EntityCreator
    public MyEntity(MyEntityCreatedEvent event) {
        this.id = event.getId();
        this.name = event.getName();
    }

    @CommandHandler
    public static void create(CreateMyEntityCommand command) {
        apply(new MyEntityCreatedEvent(command.getId(), command.getName()));
    }
}
```

The way Event-Sourced entities are constructed is defined by the `EventSourcedEntityFactory` that is passed into the
`EventSourcingRepository`. There are four possible ways to construct an entity:

1. **No-arg constructor**: This is the default behavior, where the entity is constructed using a no-arg constructor. Use
   `EventSourcedEntityFactory.fromNoArgument(...)` to use this.
2. **Identifier constructor**: The entity is constructed using a constructor that takes the identifier as a payload. Use
   `EventSourcedEntityFactory.fromIdentifier(...)` to use this.
3. **Event Message**: The entity is constructed using a constructor that takes the first event message as a payload. Use
   `EventSourcedEntityFactory.fromEventMessage(...)` to use this.
4. **Reflection**: Use the `AnnotationBasedEventSourcedEntityFactory` to construct the entity using reflection, marking
   constructors (or static methods) with the `@EntityCreator` annotation. This is the default behavior in Axon Framework.

### Creational Command Handlers

Axon Framework 5 distinguishes two types of command handlers:

1. **Creational Command Handlers**: These are static methods on the entity class that are responsible for creating the
   entity and creating the entity, for example, by publishing the first event.
2. **Instance Command Handlers**: These are instance methods on the entity class that handle commands after the entity
   has been created.

The `EntityModel` has the `handleCreate` and `handleInstance` methods to handle these two different kind of commands,
with the `EntityModelBuilder` providing the means to define these handlers. The same command can be registered as both
creational and instance command handler, allowing you to handle the command in a static method and an instance method
depending on whether the entity is already created or not.

Here is an example of both a creational and an instance command handler in Java:

```java
public class MyEntity {

    @AggregateIdentifier
    private String id;

    @EntityCreator
    public MyEntity(MyEntityCreatedEvent event) {
        this.id = event.getId();
        // Other initialization logic...
    }

    // Creational command handler
    @CommandHandler
    public static void create(CreateMyEntityCommand command) {
        apply(new MyEntityCreatedEvent(command.getId(), command.getName()));
    }

    // Instance command handler
    @CommandHandler
    public void handle(UpdateMyEntityCommand command) {
        apply(new MyEntityUpdatedEvent(id, command.getNewName()));
        // Other update logic...
    }
}
```

### Reflection-based entities

While very similar to the reflection-based aggregates from AF4, reflection-based entities have gained some new capabilities.

First, it is now possible to define two or more children of the same type. 
Note that the `@EntityMember#commandTargetResolver` must resolve to only one value over all children.

```java
public abstract class Project {

    @EntityMember
    private List<Developer> otherDevelopers = new ArrayList<>();

    @EntityMember
    private List<Milestone> features = new ArrayList<>();
}
```

Second, the `@EntityMember#commandTargetResolver` can now be customized. 
By creating your own definition, you can route the command target using something else than the `@RoutingKey`.

```java
public class Project {

    @EntityMember(commandTargetResolver = AwesomeCommandTargetDefinition.class)
    private List<Milestone> features = new ArrayList<>();

    private static class AwesomeCommandTargetDefinition implements CommandTargetResolverDefinition {

        @Nonnull
        @Override
        public <E> CommandTargetResolver<E> createCommandTargetResolver(@Nonnull AnnotatedEntityModel<E> entity,
                                                                        @Nonnull Member member) {
            return (candidates, message, context) -> {
                return candidates.stream().filter(d -> d.isAwesome()).findFirst().orElse(null);
            };
        }
    }
}
```

Third, in Axon Framework 4, the default was to forward events to all entities by default. In Axon Framework 5, this
has changed to only forward events to entities that match the routing key. You can always customize this behavior
by providing a custom `@EntityMember#eventRoutingResolver`:

```java
public abstract class Project {

    @EntityMember(eventTargetMatcher = CustomEventTargetMatcher.class)
    private List<Milestone> features = new ArrayList<>();

    private static class CustomEventTargetMatcher implements EventTargetMatcherDefinition {

        @Nonnull
        @Override
        public <E> EventTargetMatcher<E> createEventRoutingResolver(@Nonnull AnnotatedEntityModel<E> entity,
                                                                    @Nonnull Member member) {
            return (entity, message, ctx) -> {
                return entity.isMostImportantMilestone();
            };
        }
    }
}
```

Fourth, `@EntityMember` can now be used on fields with a simple type, or a `List`. Other types of collections can
currently not be used.
This is due to a limitation of the immutability of child entities that we now support. We might support this in the
future, but for now, we recommend using a `List` or a simple type.

### Exception mapping

With the change from Aggregate to Entity, we've also changed some exceptions. If you depends on these
exceptions, you will need to change your code. The following table shows the changes:

| Old Exception                                                          | New Exception                                                     |
|------------------------------------------------------------------------|-------------------------------------------------------------------|
| `org.axonframework.modelling.command.AggregateEntityNotFoundException` | `org.axonframework.modelling.entity.ChildEntityNotFoundException` |

## Test Fixtures

The `axon-test` module of Axon Framework has historically provided two different test fixtures:

1. The `AggregateTestFixture`
2. The `SagaTestFixture`

Both provide a given-when-then style of testing, based on the messages going in and out of the aggregate and saga.
Although practical, we have encountered a couple of predicaments with this style over the years:

1. It is very easy to miss a part of the application configuration with the test fixtures. Although the fixtures have
   numerous registration methods for all things important with aggregate and saga testing, this does not resolve the
   case that somebody might simply forget to add the configuration in both the production and the test scenario.
2. Testing is limited to aggregates and sagas. Hence, Event Handling Components, or Projectors/Projections, do not have
   testing support at all.
3. The test fixtures do not support a form of integration testing. Differently put, it is not possible to validate
   whether the aggregate process (for example) flows through the upcaster process, or triggers snapshots.

In Axon Framework 5, we resolve this by replacing both fixtures with the `AxonTestFixture`. The `AxonTestFixture` is
created by inserting the `ApplicationConfigurer`. From there, it provides the usual given-when-then style of testing.
Any form of message can initiate the given-phase, any form of message can influence the when-phase, and any form of
message can be expected in the then-phase.

By basing the fixture on the `ApplicationConfigurer`, we resolve the concern that users might forget to add
configuration to their fixture that's used in their (production) system. Furthermore, by having the **entire**
`ApplicationConfigurer`, we can easily expand the test fixture to incorporate other areas for testing, like
snapshotting, dead-letter queues, and event scheduling (to name a few). And, lastly, it should serve as an easier
solution towards integration testing an Axon Framework application.

We acknowledge that this shift is a massive breaking changes between Axon Framework 4 and 5. Given the importance of
test suites, we will provide a legacy installment of the old fixtures, albeit deprecated. This way, users are able to
migrate the tests on their own pass.

Minor API Changes
=================

* The `Repository`, just as other components, has been made [async native](#adjusted-apis). This means methods return a
  `CompletableFuture` instead of the loaded `Aggregate`. Furthermore, the notion of aggregate was removed from the
  `Repository`, in favor of talking about `ManagedEntity` instances. This makes the `Repository` applicable for
  non-aggregate solutions too.
* The `EventBus` has been renamed to `EventSink`, with adjusted APIs. All publish methods now expect a `String context`
  to define in which (bounded-)context an event should be published. Furthermore, either the method holding the
  `ProcessingContext` or the `publish` returning a `CompletableFuture<Void>` should be used, as these make it possible
  to perform the publication asynchronously.
* The `StreamableEventSource` replaces the `StreamableMessageSource`, enforcing the `Message` type streamed to an
  `EventMessage` implementation. Furthermore, the `StreamableMessageSource#openStream` returns a `MessageStream` instead
  of a `BlockingStream`, taking a `StreamingCondition` (that can be based on a `TrackingToken`) as input. Lastly, all
  `TrackingToken` methods now return a `CompletableFuture<TrackingToken>`, signaling they're potential asynchronous
  operations.
* To append events within an aggregate / entity, use the `EventAppender#append` instead of the
  `AggregateLifecycle#apply` method.
* The `EventStorageEngine` uses append, source, and streaming conditions, for appending, sourcing, and streaming events,
  as described in the [Event Store](#event-store) section. Furthermore, operations have been made "async-native," as
  described [here](#adjusted-apis). This is marked as a minor API changes since the `EventStorageEngine` should not be
  used directly
* The `RollbackConfiguration` interface and the `rollbackConfiguration()` builder method have been removed from all
  EventProcessor builders. Exceptions need to be handled by an interceptor, or otherwise they are always considered an
  error.
* The `Lifecycle` interface has been removed, as component lifecycle management is done on component registration. This
  allows component construction to be lazy instead of eager, since we do not require an active instance anymore (as was
  the case with the `Lifecycle` interface). Please read
  the [Component Lifecycle Management](#component-lifecycle-management) section for more details on this.

Stored Format Changes
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

Class and Method Changes
========================

## Class Changes

This section contains five tables:

1. [Moved or Renamed Classes](#moved-or-renamed-classes)
2. [Removed Classes](#removed-classes)
3. [Classes marked for removal](#marked-for-removal-classes)
4. [Changed implements or extends](#changed-implements-or-extends)
5. [Adjusted Constants](#adjusted-constants)

### Moved or Renamed Classes

| Axon 4                                                                                                 | Axon 5                                                                           | Module change?                 |
|--------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|--------------------------------|
| org.axonframework.common.caching.EhCache3Adapter                                                       | org.axonframework.common.caching.EhCacheAdapter                                  | No                             |
| org.axonframework.eventsourcing.MultiStreamableMessageSource                                           | org.axonframework.eventhandling.MultiStreamableMessageSource                     | No                             |
| org.axonframework.eventhandling.EventBus                                                               | org.axonframework.eventhandling.EventSink                                        | No                             |
| org.axonframework.commandhandling.CommandHandler                                                       | org.axonframework.commandhandling.annotation.CommandHandler                      | No                             |
| org.axonframework.eventhandling.EventHandler                                                           | org.axonframework.eventhandling.annotation.EventHandler                          | No                             |
| org.axonframework.queryhandling.QueryHandler                                                           | org.axonframework.queryhandling.annotation.QueryHandler                          | No                             |
| org.axonframework.config.Configuration                                                                 | org.axonframework.configuration.Configuration                                    | Yes. Moved to `axon-messaging` |
| org.axonframework.config.Component                                                                     | org.axonframework.configuration.Component                                        | Yes. Moved to `axon-messaging` |
| org.axonframework.config.ConfigurerModule                                                              | org.axonframework.configuration.ConfigurationEnhancer                            | Yes. Moved to `axon-messaging` |
| org.axonframework.config.ModuleConfiguration                                                           | org.axonframework.configuration.Module                                           | Yes. Moved to `axon-messaging` |
| org.axonframework.config.LifecycleHandler                                                              | org.axonframework.configuration.LifecycleHandler                                 | Yes. Moved to `axon-messaging` |
| org.axonframework.config.LifecycleOperations                                                           | org.axonframework.configuration.LifecycleRegistry                                | Yes. Moved to `axon-messaging` |
| org.axonframework.commandhandling.CommandCallback                                                      | org.axonframework.commandhandling.gateway.CommandResult                          | No                             |
| org.axonframework.commandhandling.callbacks.FutureCallback                                             | org.axonframework.commandhandling.gateway.FutureCommandResult                    | No                             |
| org.axonframework.modelling.command.Repository                                                         | org.axonframework.modelling.repository.Repository                                | No                             |
| org.axonframework.modelling.command.CommandTargetResolver                                              | org.axonframework.modelling.command.EntityIdResolver                             | No                             |
| org.axonframework.modelling.command.ForwardingMode                                                     | org.axonframework.modelling.command.entity.child.EventTargetMatcher              | No                             |
| org.axonframework.modelling.command.AggregateMember                                                    | org.axonframework.modelling.entity.annotation.EntityMember                       | No                             |
| org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory                      | org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel           | No                             |
| org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityCollectionDefinition | org.axonframework.modelling.entity.annotation.ListEntityModelDefinition          | No                             |
| org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityDefinition           | org.axonframework.modelling.entity.annotation.SingleEntityChildModelDefinition   | No                             |
| org.axonframework.modelling.command.inspection.AbstractChildEntityDefinition                           | org.axonframework.modelling.entity.annotation.AbstractEntityChildModelDefinition | No                             |
| org.axonframework.axonserver.connector.ServerConnectorConfigurerModule                                 | org.axonframework.axonserver.connector.ServerConnectorConfigurationEnhancer      | No                             |
| org.axonframework.serialization.CannotConvertBetweenTypesException                                     | org.axonframework.serialization.ConversionException                              | No                             |

### Removed Classes

| Class                                                                                    | Why                                                                                                                                            |
|------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| org.axonframework.config.Configurer                                                      | Made obsolete through introduction of several `ApplicationConfigurer` instances (see [Configuration](#applicationconfigurer-and-configuration) |
| org.axonframework.messaging.unitofwork.AbstractUnitOfWork                                | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.messaging.unitofwork.BatchingUnitOfWork                                | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.messaging.unitofwork.CurrentUnitOfWork                                 | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.messaging.unitofwork.DefaultUnitOfWork                                 | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.messaging.unitofwork.ExecutionResult                                   | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.messaging.unitofwork.MessageProcessingContext                          | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work))                                                      |
| org.axonframework.eventsourcing.eventstore.AbstractEventStore                            | Made obsolete through the rewrite of the `EventStore` (see [Event Store](#event-store).                                                        |
| org.axonframework.modelling.command.AggregateLifecycle                                   | Made obsolete through the rewrite of the `EventStore` (see [Event Store](#event-store).                                                        |
| org.axonframework.eventsourcing.conflictresolution.ConflictDescription                   | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.ConflictExceptionSupplier             | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.ConflictResolution                    | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.ConflictResolver                      | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.Conflicts                             | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.ContextAwareConflictExceptionSupplier | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.DefaultConflictDescription            | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.DefaultConflictResolver               | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.eventsourcing.conflictresolution.NoConflictResolver                    | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.modelling.command.ConflictingAggregateVersionException                 | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.modelling.command.ConflictingModificationException                     | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.modelling.command.TargetAggregateVersion                               | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.modelling.command.VersionedAggregateIdentifier                         | No longer supported in Axon Framework 5 due to limited use by the community.                                                                   |
| org.axonframework.lifecycle.Lifecycle                                                    | [Lifecycle management](#component-lifecycle-management) is now only done lazy, eliminating the need for concrete component scanning.           |
| org.axonframework.config.LifecycleHandlerInspector                                       | [Lifecycle management](#component-lifecycle-management) is now only done lazy, eliminating the need for concrete component scanning.           |
| org.axonframework.lifecycle.StartHandler                                                 | [Lifecycle management](#component-lifecycle-management) is now only done lazy, eliminating the need for concrete component scanning.           |
| org.axonframework.lifecycle.ShutdownHandler                                              | [Lifecycle management](#component-lifecycle-management) is now only done lazy, eliminating the need for concrete component scanning.           |

### Marked for removal Classes

All classes in this table have been moved to the legacy package for ease in migration.
However, they will eventually be removed entirely from Axon Framework 5, as we expect users to migrate to the new (and
per class described) approach.

| Class                                          |
|------------------------------------------------|
| org.axonframework.modelling.command.Repository |

### Changed implements or extends

Note that **any**  changes here may have far extending impact on the original class.

| Class      | Before           | After            | Explanation                                                  | 
|------------|------------------|------------------|--------------------------------------------------------------|
| `MetaData` | `Map<String, ?>` | `Map<String, ?>` | See the [metadata description](#metadata-with-string-values) |

### Adjusted Constants

| Class                | Constant          | Change   | Why                                   |
|----------------------|-------------------|----------|---------------------------------------|
| `HandlerAttributes`  | `START_PHASE`     | Removed  | StartHandler annotation is removed    |
| `HandlerAttributes`  | `SHUTDOWN_PHASE`  | Removed  | ShutdownHandler annotation is removed |

## Method Signature Changes

This section contains four subsections, called:

1. [Constructor Parameter adjustments](#constructor-parameter-adjustments)
2. [Moved methods and constructors](#moved--renamed-methods-and-constructors)
3. [Removed methods and constructors](#removed-methods-and-constructors)
4. [Changed Method return types](#changed-method-return-types)

### Constructor Parameter adjustments

| Constructor                                                                                | What                         | Why                                          | 
|--------------------------------------------------------------------------------------------|------------------------------|----------------------------------------------|
| One org.axonframework.messaging.AbstractMessage constructor                                | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| One org.axonframework.serialization.SerializedMessage constructor                          | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.messaging.GenericMessage constructors                      | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.commandhandling.GenericCommandMessage constructors         | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.eventhandling.GenericEventMessage constructors             | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.eventhandling.GenericDomainEventMessage constructors       | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericQueryMessage constructors             | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericSubscriptionQueryMessage constructors | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericStreamingQueryMessage constructors    | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.deadline.GenericDeadlineMessage constructors               | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.messaging.GenericResultMessage constructors                | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.commandhandling.GenericCommandResultMessage constructors   | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All none-copy org.axonframework.queryhandling.GenericQueryResponseMessage constructors     | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |
| All org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage constructors     | Added the `MessageType` type | See [here](#message-type-and-qualified-name) |

### Moved, Renamed, or parameter adjusted Methods

| Constructor / Method                                                                                                          | To where                                                                                                               |
|-------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `Configurer#configureCommandBus`                                                                                              | `MessagingConfigurer#registerCommandBus`                                                                               | 
| `Configurer#configureEventBus`                                                                                                | `MessagingConfigurer#registerEventSink`                                                                                | 
| `Configurer#configureQueryBus`                                                                                                | `MessagingConfigurer#registerQueryBus`                                                                                 | 
| `Configurer#configureQueryUpdateEmitter`                                                                                      | `MessagingConfigurer#registerQueryUpdateEmitter`                                                                       | 
| `ConfigurerModule#configureModule`                                                                                            | `ConfigurationEnhancer#enhance`                                                                                        | 
| `ConfigurerModule#configureLifecyclePhaseTimeout`                                                                             | `LifecycleRegistry#registerLifecyclePhaseTimeout`                                                                      | 
| `Configurer#registerComponent(Function<Configuration, ? extends C>)`                                                          | `ComponentRegistry#registerComponent(ComponentBuilder<C>)`                                                             | 
| `Configurer#registerModule(ModuleConfiguration)`                                                                              | `ComponentRegistry#registerComponent(Module)`                                                                          | 
| `StreamableMessageSource#openStream(TrackingToken)`                                                                           | `StreamableEventSource#open(SourcingCondition)`                                                                        | 
| `StreamableMessageSource#createTailToken()`                                                                                   | `StreamableEventSource#firstToken()`                                                                                   | 
| `StreamableMessageSource#createHeadToken()`                                                                                   | `StreamableEventSource#latestToken()`                                                                                  | 
| `StreamableMessageSource#createTokenAt(Instant)`                                                                              | `StreamableEventSource#tokenAt(Instant)`                                                                               | 
| `Repository#newInstance(Callable<T>)`                                                                                         | `Repository#persist(ID, T, ProcessingContext)`                                                                         | 
| `Repository#load(String)`                                                                                                     | `Repository#load(ID, ProcessingContext)`                                                                               | 
| `Repository#loadOrCreate(String, Callable<T>)`                                                                                | `Repository#loadOrCreate(ID, ProcessingContext)`                                                                       | 
| `EventStore#readEvents(String)`                                                                                               | `EventStoreTransaction#source(SourcingCondition)`                                                                      | 
| `EventStorageEngine#readEvents(EventMessage<?>...)`                                                                           | `EventStorageEngine#appendEvents(AppendCondition, TaggedEventMessage...)`                                              | 
| `EventStorageEngine#appendEvents(List<? extends EventMessage<?>>)`                                                            | `EventStorageEngine#appendEvents(AppendCondition, List<TaggedEventMessage<?>>)`                                        | 
| `EventStorageEngine#appendEvents(List<? extends EventMessage<?>>)`                                                            | `EventStorageEngine#appendEvents(AppendCondition, List<TaggedEventMessage<?>>)`                                        | 
| `EventStorageEngine#readEvents(String)`                                                                                       | `EventStorageEngine#source(SourcingCondition)`                                                                         | 
| `EventStorageEngine#readEvents(String, long)`                                                                                 | `EventStorageEngine#source(SourcingCondition)`                                                                         | 
| `EventStorageEngine#readEvents(TrackingToken, boolean)`                                                                       | `EventStorageEngine#stream(StreamingCondition)`                                                                        | 
| `EventStorageEngine#createTailToken()`                                                                                        | `EventStorageEngine#firstToken()`                                                                                      | 
| `EventStorageEngine#createHeadToken()`                                                                                        | `EventStorageEngine#latestToken()`                                                                                     | 
| `EventStorageEngine#createTokenAt(Instant)`                                                                                   | `EventStorageEngine#tokenAt(Instant)`                                                                                  | 
| `StreamingEventProcessor#resetTokens(Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken>)`               | `StreamingEventProcessor#resetTokens(Function<TrackingTokenSource, CompletableFuture<TrackingToken>>)`                 |
| `StreamingEventProcessor#resetTokens(Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken>, R resetContext)` | `StreamingEventProcessor#resetTokens(Function<TrackingTokenSource, CompletableFuture<TrackingToken>>, R resetContext)` |
| `PooledStreamingEventProcessor.Builder#initialToken(Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken>)` | `PooledStreamingEventProcessor.Builder#initialToken(Function<TrackingTokenSource, CompletableFuture<TrackingToken>>)`  |
| `PooledStreamingEventProcessor.Builder#messageSource(StreamableMessageSource<TrackedEventMessage<?>>)`                        | `PooledStreamingEventProcessor.Builder#eventSource(StreamableEventSource<? extends EventMessage<?>>)`                  |
| `Converter#convert(Object, Class<T>)`                                                                                         | `Converter.#convert(S, Class<T>)`                                                                                      |
| `Converter#convert(Object, Class<?>, Class<T>)`                                                                               | `Converter.#convert(S, Class<S>, Class<T>)`                                                                            |

### Removed Methods and Constructors

| Constructor / Method                                                                                 | Why                                                                                         | 
|------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `org.axonframework.config.ModuleConfiguration#initialize(Configuration)`                             | Initialize is now replace fully by start and shutdown handlers.                             |
| `org.axonframework.config.ModuleConfiguration#unwrap()`                                              | Unwrapping never reached its intended use in AF3 and AF4 and is thus redundant.             |
| `org.axonframework.config.ModuleConfiguration#isType(Class<?>)`                                      | Only use by `unwrap()` that's also removed.                                                 |
| `org.axonframework.config.Configuration#lifecycleRegistry()`                                         | A round about way to support life cycle handler registration.                               |
| `org.axonframework.config.Configurer#onInitialize(Consumer<Configuration>)`                          | Fully replaced by start and shutdown handler registration.                                  |
| `org.axonframework.config.Configurer#defaultComponent(Class<T>, Configuration)`                      | Each Configurer now has get optional operation replacing this functionality.                |
| `org.axonframework.messaging.StreamableMessageSource#createTokenSince(Duration)`                     | Can be replaced by the user with an `StreamableEventSource#tokenAt(Instant)` invocation.    |
| `org.axonframework.modelling.command.Repository#load(String, Long)`                                  | Leftover behavior to support aggregate validation on subsequent invocations.                |
| `org.axonframework.modelling.command.Repository#newInstance(Callable<T>, Consumer<Aggregate<T>>)`    | No longer necessary with replacement `Repository#persist(ID, T, ProcessingContext)`.        |
| `org.axonframework.eventsourcing.eventstore.EventStore#readEvents(String)`                           | Replaced for the `EventStoreTransaction` (see [appending events](#appending-events).        | 
| `org.axonframework.eventsourcing.eventstore.EventStore#readEvents(String, long)`                     | Replaced for the `EventStoreTransaction` (see [appending events](#appending-events).        | 
| `org.axonframework.eventsourcing.eventstore.EventStore#storeSnapshot(DomainEventMessage<?>)`         | Replaced for a dedicated `SnapshotStore`.                                                   |
| `org.axonframework.eventsourcing.eventstore.EventStore#lastSequenceNumberFor(String)`                | No longer necessary to support through the introduction of DCB.                             |
| `org.axonframework.eventsourcing.eventstore.EventStorageEngine#storeSnapshot(DomainEventMessage<?>)` | Replaced for a dedicated `SnapshotStore`.                                                   |
| `org.axonframework.eventsourcing.eventstore.EventStorageEngine#readSnapshot(String)`                 | Replaced for a dedicated `SnapshotStore`.                                                   |
| `org.axonframework.eventsourcing.eventstore.EventStorageEngine#lastSequenceNumberFor(String)`        | No longer necessary to support through the introduction of DCB.                             |
| `org.axonframework.eventsourcing.CachingEventSourcingRepository#validateOnLoad(Aggregate<T>, Long)`  | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.eventsourcing.CachingEventSourcingRepository#doLoadWithLock(String, Long)`        | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.eventsourcing.EventSourcingRepository#doLoadWithLock(String, Long)`               | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.AbstractRepository#load(String, Long)`                          | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.GenericJpaRepository#doLoadWithLock(String, Long)`              | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.LockingRepository#doLoad(String, Long)`                         | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.LockingRepository#doLoadWithLock(String, Long)`                 | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.Repository#load(String, Long)`                                  | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.Aggregate#version()`                                            | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.modelling.command.LockAwareAggregate#version()`                                   | Version-based loading is no longer supported due to limited use by the community.           |
| `org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager.Builder#startScheduler(boolean)`  | [Lifecycle management](#component-lifecycle-management) has become a configuration concern. |
| `org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager.Builder#stopScheduler(boolean)`   | [Lifecycle management](#component-lifecycle-management) has become a configuration concern. |

### Changed Method return types

| Method                                         | Before                           | After            |
|------------------------------------------------|----------------------------------|------------------|
| `CorrelationDataProvider#correlationDataFor()` | `Map<String, String>`            | `Map<String, ?>` | 
| `CommandTargetResolver#resolveTarget`          | `VersionedAggregateIdentifier`   | `String`         |