# Design principles

## Base
- Use JDK17 as the base.
- Make the framework support reactive programming, but not enforce it.
  In doing so, users can stick to what they're used (with Axon Framework 4) to or select Project Reactor.
  To that end, the "edges" of the Framework allow a reactive solution.
  In other words, both dispatching and handling, the users domain, should allow imperative and reactive programming.
  Internally, we should not be biased upon a reactivestreams implementation, like Project Reactor, however.
  This will provide us the most flexibility, without sacrificing the choices of the user.
- Decide upon project modularity.
  E.g. should we split axon-messaging into events/commands/queries?
  Or move core components to axon-core?.
- Extract Spring, JPA, and JDBC from main project into extensions.
* Selectively open up APIs to end users, to allow us to change things even after a release.
  Thus, giving us more flexibility in designing.

## UnitOfWork
- UnitOfWork should not be accessible to end users. 
  Instead, they should only be able to interact with its lifecycle (e.g., add commit-phase operations) and context (to add resources).
  This guards against users accidentally invoking, for example, UnitOfWork#commit (which they should never do).
- The UnitOfWork, or ProcessingLifecycle + ProcessingContext, are inclined to provide a means to support both reactive and imperative programming.
  We can achieve this by letting the UoW implementation expected a Publisher and return a wrapped Publisher, used by every message handling method.
  This custom Publisher will:
  1. Allow us to support any reactivestreams implementation from a handler,
  2. Allow us to map other results like `void`, `Object`, `CompletableFuture<Void>` and `CompletableFuture<Object>` to a `Publisher`, and
  3. Allow us to hook into the `Publisher` steps with the UoW's lifecycle logic.
- Note that this custom `Publisher` for the Framework is not intended to be a full implementation.
  It just does the mapping from a given `Publisher` on too the one invoking the wrapper, and vice-versa.
- To ensure correct context usage between imperative or reactive-styled programming, we should map any fields of the received edge context on too the Axon context.
  For example, we should copy over the Project Reactor `Context` unto the `ProcessingContext`, and vice versa.
  Doing so, we ensure that, for example, both Spring's Imperative and Reactive Transaction logic is maintained whenever the users code enters the framework.

## Messages
* Disconnect message name from payload type. 
  This means during handler subscription, that you need to provide a name. 
  Annotation based may default to the FQCN, still.
* Make the notion of 'namespaces' to all messages explicit. 
  This is already present at the moment, but it's part of the payloadType. 
  Exposes this directly allows a (cleaner) mapping from messages-to-namespace, and namespaces-to-context.
* Usage of the namespaces may also allow an easier integration of multi tenancy within the core of the Framework.

## Message Buses
- Bus implementations should allow provisioning of dispatch information.
  For example, does a Message have a certain priority over others.
- The Message Buses will provide a common approach to register "Message Handling Components" (MHC for short).
  Some form of `HandlerRegistry` interface is thus reasonable, allow the registration of a MHC.
  In doing so, we synchronize the three types of buses, whereas for Axon Framework 4, Event Processors are the oddball for this.
- A Message Handling Component may in turn consist out of other Message Handling Components, if the layering so requires it.
- We will provide distinct Gateway interfaces for the different types of outward interaction with the Framework.
  For example, whenever a user sticks to the imperative proramming paradigm, the (regular) `CommandGateway` is used.
  If a users prefers Project Reactor they should use the `ReactorCommandGateway`, clearly defining `Mono`/`Flux` operations.
  And if a user would like to use Kotlin's coroutines, they should wire a distinct `CoroutineCommandGateway`.
  Without doing so, we can not guarantee that we can map the respective context implementations (e.g., `ThreadLocal` or `Context` (Project Reactor)) over to Axon Framework's `ProcessingContext`.

## Message Intercepting
- Dispatch Interceptors should allow reaction to the responses of handling the message(s).
  Or, distinct Result Interceptors should be present throughout the Framework's bus implementations.
- Distinct interceptor support interfaces should not be necessary.
  Instead, the constructors/builders of the respective bus implementations should allow provisioning of a a single instance.
  This single instance internally represents the chain of interceptors to utilize before dispatching, handling, or result returning.

## Message Handling
* A Message Handler should be capable of defining the business name of the message it handles,
  and the type it wants to receive it in.
- A generic form of "stateful message handler" is a beneficial for any message handler in the system.
  For example, stateful command handlers would be a way to deal differently with your Command Model than the current aggregate approach.
  Similarly, a stateful event handler can mitigate the situation where a users needs to wire the Repository manually.
  And, (e.g.) we can ditch the Saga!!! Because that becomes a stateful event handler too.
- The described breakdown allows us to derive new combinations of message handlers.
  This should support any style/archetype of Message Handling Component.

## Event Processing / Token Maintenance 
* Experiment whether we can remove the Event Processor to Processing Group layering.
  Thus, can we do without Processing Groups to simplify configuration?

## Event Scheduling
- Event scheduling should schedule the event inside the Event Store.
  The fact they're currently separated over different storage solution may incur problematic scenarios.
  Hence, assuring they're in the same store will mitigate this.

## Configuration
- Break up Configuration module, to not have one module that depends on all other modules.
- Define Message Handling Component configuration (MHC-configuration), without Annotations.
* Drop default Serializer, to enforce users to think about the Serializer to use.
* Revamp the configuration to allow a 'higher-level' configuration component,
   like a "Command Handling Component Configuration" or "Command Center Configuration".
  Through this, we can have a user define a message handler, appending any type of additional behavior required.
  Furthermore, this allows us to eliminate unclear config options (e.g., why have a Parameter Resolver for the Repository?).
  Instead, we are then able to take the users configuration, 
   and wrap the behavior of the infrastructure components.
  Simply put, use the Decorator Pattern.
- Favor direct component configuration i.o. Service Loader usage.
* Dynamic configuration changes?

## Annotations
- Define annotation-based Message Handling Component setup, using the MHC-configuration
- HandlerEnhancers and ParameterResolvers are purely intended for annotation based MHCs.
* Ahead of time?

## Serialization / Upcasting
- Messages should not be serialization native. 
  The message buses need to be serialization aware, though. 
  They should, as these know the message handlers, and what the expected type to handle is. 
  Thus, handlers need to register themselves with the desired message name.
- Attach upcasting to the serialization-process / Serializer.
- Enforce serialized format of internal objects, e.g. tokens.
  This eliminates issues with de-/serialization with different Serializer choices.
  Taking the token example further, looking at the `GlobalIndexTrackingToken`, all we require is the `globalIndex`.
  Pushing that object through in customizable serializer does not provide benefit over simply storing the index.
  So, in short, we drop the `generic` serializer option.
  This does require us to find a solution for snapshot serialization, which uses the `generic` serializer.
- Serializers are configured on the buses.
- Consider renaming `Serializer` to `Converter`, as all the current serializer does is convert from one format to another.
  Or in other terms, it maps.
  This name switch allows the `Converter` to (1) provide the roll of the (AF4) Serializer and (2) support Upcasting.
- The `axon-legacy` (or `axon-vintage`?!) module should allow a transition from the (AF4) `Upcaster` solution to the new `Converter` approach.

## Snapshotting
- Snapshot triggering, creation, and usage should be more easily definable by the end user
  This point stems from the assumed lack of XStream serialization simplicity, that "simply works."
  Using another format, like Jackson, currently requires introduction of getter/setter logic; code that doesn't belong in an Aggregate.
- Employee snapshotting in test fixtures.

## Testing
- Have Aggregate Test Fixtures ingest the Aggregate Configuration, to base the test suite on.
- Aggregate Test Fixtures should, if configured, validate the given scenario's state with the snapshot state.
  Doing so, we guard users against incorrectly defining the snapshot state of their aggregates.

## Commands / Command Modelling / Aggregates
- (Annotated) aggregates as they currently exist inside Axon Framework should stick.
  The underlying implementation will very likely differ, taking the "Kill the Aggregate!" presentation in mind.
- Users should be able to configure an Aggregate through (1) Annotations, and (2) declarative configuration.
  This declarative configuration allows users to be "more pure" on a DDD-level, as they do not have to use framework logic inside the model.
  Furthermore, the declarative configuration allows definitions like "this event('s state) is handled by these methods," or "this command is handled by this function, resulting in these events."
- The current AggregateLifecycle#apply method obstructs the fact it will *first* handle the event inside the aggregate and then move back to the command handler.
  This obscures the fact subsequent tasks inside the command handler invoking *apply* can rely on that state change.
  Although clarified in the JavaDoc, finding an explicit means to dictate "apply this event to the current state and then proceed."

## Queries
* Merge Direct and Scatter-Gather into the Streaming Query API.
  We can achieve this by adjusting the (handler) cardinality of the Streaming Query operation.
  E.g. cardinality of one would mean a direct query format, and N is scatter gather.
  Intent for this approach is to simplify the Query API for users.
- Simplify / rethink the subscription query API.
  Explaining the AF4 format raises eyebrows for users at the moment.
  So, seeing how we can either wrap the support in the Streaming Query API, is beneficial.
  Note that it does serve a different purpose at the base: an initial response and updates (from N locations).

## Stateful-EventHandler (Sagas / ProcessManager)
- We drop the notion of Sagas in the framework, in favor for Stateful-EventHandlers.
  The Stateful-EventHandlers follows the ["stateful message handler"](#message-handling) approach.
  A component describing the "process" (in AF4 resolved by a Saga/ProcessManager) should be composable from these stateful-event-handlers.
- We should no longer store sagas/processes ourselves. 
  Thus, whenever the process-archetype is used, the user should define how the state is stored and retrieved.

## Deadlines
- We agree that the current API, which assumes a DeadlineMessage to be an EventMessage, to be incorrect.
  A DeadlineMessage should be its own Message entirely,
  follow command routing rules when targeted towards an Aggregate and Event routing rules when targeted towards a Saga.
- Taking note of the state message handler idea under "Message Handling"
  should proof as a guideline to design the deadline support within Axon Framework 5. 
- We need to take into consideration that Deadlines are a technical solution to real world problem
  and *are not* a concept that resides in Domain-Driven Design. 

## Monitoring / Tracing
* 

## Deprecation
* Should we pre-deprecate stuff that we'll remove in AF5?
* If we will remove stuff that's not already deprecated, of course. (Disruptor, ConflictResolution, Sagas)

## Rules
- No ThreadLocals internal to the Framework! Only on the edges, purely for the imperative style.
- No XStream! 
- No static methods on our public APIs!
- No locks / `synchronized` keywords!
- No `Thread#sleep`!
- No Exception throwing in the functional-coding style!
- No schema maintenance!