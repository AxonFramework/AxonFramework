Architectural Overview
======================
{prev=1-introduction}
{next=3-command-handling}

CQRS on itself is a very simple pattern. It only describes that the component of an application that processes commands should be separated from the component that processes queries. Although this separation is very simple on itself, it provides a number of very powerful features when combined with other patterns. Axon provides the building block that make it easier to implement the different patterns that can be used in combination with CQRS.

The diagram below shows an example of an extended layout of a CQRS-based event driven architecture. The UI component, displayed on the left, interacts with the rest of the application in two ways: it sends commands to the application (shown in the top section), and it queries the application for information (shown in the bottom section).

![Architecture overview of a CQRS application](images/detailed-architecture-overview.png)

Commands are typically represented by simple and straightforward objects that contain all data necessary for a command handler to execute it. A command expresses its intent by its name. In Java terms, that means the class name is used to figure out what needs to be done, and the fields of the command provide the information required to do it.

The Command Bus receives commands and routes them to the Command Handlers. Each command handler responds to a specific type of command and executes logic based on the contents of the command. In some cases, however, you would also want to execute logic regardless of the actual type of command, such as validation, logging or authorization.

Axon provides building blocks to help you implement a command handling infrastructure with these features. These building blocks are thoroughly described in [Command Handling](3-command-handling.md#command-handling).

The command handler retrieves domain objects (Aggregates) from a repository and executes methods on them to change their state. These aggregates typically contain the actual business logic and are therefore responsible for guarding their own invariants. The state changes of aggregates result in the generation of Domain Events. Both the Domain Events and the Aggregates form the domain model. Axon provides supporting classes to help you build a domain model. They are described in [Domain Modeling](4-domain-modeling.md#domain-modeling).

Repositories are responsible for providing access to aggregates. Typically, these repositories are optimized for lookup of an aggregate by its unique identifier only. Some repositories will store the state of the aggregate itself (using Object Relational Mapping, for example), while other store the state changes that the aggregate has gone through in an Event Store. The repository is also responsible for persisting the changes made to aggregates in its backing storage.

Axon provides support for both the direct way of persisting aggregates (using object-relational-mapping, for example) and for event sourcing. More about repositories and event stores can be found in [Repositories and Event Stores](5-repositories-and-event-stores.md#repositories-and-event-stores).

The event bus dispatches events to all interested event listeners. This can either be done synchronously or asynchronously. Asynchronous event dispatching allows the command execution to return and hand over control to the user, while the events are being dispatched and processed in the background. Not having to wait for event processing to complete makes an application more responsive. Synchronous event processing, on the other hand, is simpler and is a sensible default. Synchronous processing also allows several event listeners to process events within the same transaction.

Event listeners receive events and handle them. Some handlers will update data sources used for querying while others send messages to external systems. As you might notice, the command handlers are completely unaware of the components that are interested in the changes they make. This means that it is very non-intrusive to extend the application with new functionality. All you need to do is add another event listener. The events loosely couple all components in your application together.

In some cases, event processing requires new commands to be sent to the application. An example of this is when an order is received. This could mean the customer's account should be debited with the amount of the purchase, and shipping must be told to prepare a shipment of the purchased goods. In many applications, logic will become more complicated than this: what if the customer didn't pay in time? Will you send the shipment right away, or await payment first? The saga is the CQRS concept responsible for managing these complex business transactions.

The building blocks related to event handling and dispatching are explained in [Event Processing](6-event-listeners.md#event-processing). Sagas are thoroughly explained in [Sagas](7-sagas.md#managing-complex-business-transactions).

The thin data layer in between the user interface and the data sources provides a clearly defined interface to the actual query implementation used. This data layer typically returns read-only DTO objects containing query results. The contents of these DTOs are typically driven by the needs of the User Interface. In most cases, they map directly to a specific view in the UI (also referred to as table-per-view).

Axon does not provide any building blocks for this part of the application. The main reason is that this is very straightforward and doesn't differ from the layered architecture.

Axon Module Structure
=====================

Axon Framework consists of a number of modules that target specific problem areas of CQRS. Depending on the exact needs of your project, you will need to include one or more of these modules.

As of Axon 2.1, all modules are OSGi compatible bundles. This means they contain the required headers in the manifest file and declare the packages they import and export. At the moment, only the Slf4J bundle (1.7.0 &lt;= version &lt; 2.0.0) is required. All other imports are marked as optional, although you're very likely to need others, like `org.joda.time`.

Main modules
------------

Axon's main modules are the modules that have been thoroughly tested and are robust enough to use in demanding production environments. The maven groupId of all these modules is`org.axonframework`.

The Core module contains, as the name suggests, the Core components of Axon. If you use a single-node setup, this module is likely to provide all the components you need. All other Axon modules depend on this module, so it must always be available on the classpath.

The Test modules contains test fixtures that you can use to test Axon based components, such as your Command Handlers, Aggregates and Sagas. You typically do not need this module at runtime and will only need to be added to the classpath during tests.

The Distributed CommandBus modules contains a CommandBus implementation that can be used to distribute commands over multiple nodes. It comes with a JGroupsConnector that is used to connect DistributedCommandBus implementation on these nodes.

The AMQP module provides components that allow you to build up an EventBus using an AMQP-based message broker as distribution mechanism. This allows for guaranteed-delivery, even when the Event Handler node is temporarily unavailable.

The Integration module allows Axon components to be connected to Spring Integration channels. It provides an implementation for the EventBus that uses Spring Integration channels to distribute events as well as a connector to connect another EventBus to a Spring Integration channel.

Note that the Spring Integration Core has been moved to Spring Messaging in Spring 4. If you use Spring 4 (or later), use the Axon Spring Messaging module instead.

The Spring Messages module allows Axon components to be connected to Spring Messaging (since Spring 4) channels. It provides an implementation for the EventBus that uses Spring Messaging channels to distribute events as well as a connector to connect another EventBus to a Spring Messaging channel.

MongoDB is a document based NoSQL database. The Mongo module provides an EventStore implementation that stores event streams in a MongoDB database.

Several AxonFramework components provide monitoring information. This module publishes that information over JMX. There is no configuration involved. If this module is on the classpath, statistics and monitoring information is automatically published over JMX.

Incubator modules
-----------------

Incubator modules have not undergone the same amount of testing as the main modules, are not as well documented, and may therefore not be suitable for demanding production environments. They are typically work-in-progress and may have some "rough edges". Use these modules at your own peril.

The maven groupId for incubator modules is `org.axonframework.incubator`.

Working with Axon APIs
======================

CQRS is an architectural pattern, making it impossible to provide a single solution that fits all projects. Axon Framework does not try to provide that one solution, obviously. Instead, Axon provides implementations that follow best practices and the means to tweak each of those implementations to your specific requirements.

Almost all infrastructure building blocks will provide hook points (such as Interceptors, Resolvers, etc.) that allow you to add application-specific behavior to those building blocks. In many cases, Axon will provide implementations for those hook points that fit most use cases. If required, you can simply implement your own.

Non-infrastructural objects, such as Messages, are generally immutable. This ensures that these objects are safe to use in a multi-threaded environment, without side-effects.

To ensure maximum customization, all Axon components are defined using interfaces. Abstract and concrete implementations are provided to help you on your way, but will nowhere be required by the framework. It is always possible to build a completely custom implementation of any building block using that interface.

Spring Support
==============

Axon Framework provides extensive support for Spring, but does not require you to use Spring in order to use Axon. All components can be configured programmatically and do not require Spring on the classpath. However, if you do use Spring, much of the configuration is made easier with the use of Spring's namespace support. Building blocks, such as the Command Bus, Event Bus and Saga Managers can be configured using a single XML element in your Spring Application Context.

Check out the documentation of each building block for more information on how to configure it using Spring.
