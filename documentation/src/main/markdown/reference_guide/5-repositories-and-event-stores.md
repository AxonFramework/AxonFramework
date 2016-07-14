Repositories and Event Stores
=============================

The repository is the mechanism that provides access to aggregates. The repository acts as a gateway to the actual storage mechanism used to persist the data. In CQRS, the repositories only need to be able to find aggregates based on their unique identifier. Any other types of queries should be performed against the query database, not the Repository.

In the Axon Framework, all repositories must implement the `Repository` interface. This interface prescribes three methods: `load(identifier, version)`, `load(identifier)` and `add(aggregate)`. The `load` methods allows you to load aggregates from the repository. The optional `version` parameter is used to detect concurrent modifications (see [Advanced conflict detection and resolution](#advanced-conflict-detection-and-resolution)). `add` is used to register newly created aggregates in the repository.

Depending on your underlying persistence storage and auditing needs, there are a number of base implementations that provide basic functionality needed by most repositories. Axon Framework makes a distinction between repositories that save the current state of the aggregate (see [Standard Repositories](#standard-repositories)), and those that store the events of an aggregate (see [Event Sourcing Repositories](#event-sourcing-repositories)).

Note that the Repository interface does not prescribe a `delete(identifier)` method. Deleting aggregates is done by invoking the (protected) `markDeleted()` method in an aggregate. This method is protected and not available from outside the aggregate. The motivation for this, is that the aggregate is responsible for maintaining its own state. Deleting an aggregate is a state migration like any other, with the only difference that it is irreversible in many cases. You should create your own meaningful method on your aggregate which sets the aggregate's state to "deleted". This also allows you to register any events that you would like to have published.

Repositories should use the `isDeleted()` method to find out if an aggregate has been marked for deletion. If such an aggregate is then loaded again, the repository should throw an `AggregateNotFoundException` (or when possible, an `AggregateDeletedException`). Axon's standard repository implementations will delete an aggregate from the repository, while event sourcing repositories will throw an Exception when an aggregate is marked deleted after initialization.

Standard repositories
=====================

Standard repositories store the actual state of an Aggregate. Upon each change, the new state will overwrite the old. This makes it possible for the query components of the application to use the same information the command component also uses. This could, depending on the type of application you are creating, be the simplest solution. If that is the case, Axon provides some building blocks that help you implement such a repository.

The most basic implementation of the repository is `AbstractRepository`. It takes care of the event publishing when an aggregate is saved. The actual persistence mechanism must still be implemented. This implementation doesn't provide any locking mechanism and expects the underlying data storage mechanism to provide it.

The `AbstractRepository` also ensures that activity is synchronized with the current Unit of Work. That means the aggregate is saved when the Unit of Work is committed.

If the underlying data store does not provide any locking mechanism to prevent concurrent modifications of aggregates, consider using the abstract `LockingRepository` implementation. Besides providing event dispatching logic, it will also ensure that aggregates are not concurrently modified.

You can configure the `LockingRepository` with a locking strategy. A pessimistic locking strategy is the default strategy. Pessimistic locks will prevent concurrent access to the aggregate. A custom locking strategy can be provided by implementing the `LockFactory` interface.

Deadlocks are a common problem when threads use more than one lock to complete their operation. In the case of Sagas, it is not uncommon that a command is dispatched -causing a lock to be acquired-, while still holding a lock on the aggregate that cause the Saga to be invoked. The `PessimisticLockFactory` will automatically detect an imminent deadlock and will throw a `DeadlockException` before the deadlock actually occurs. It is safe to retry the operation once all nested Units of Work have been rolled back (to ensure all locks are released). The `CommandGateway` will not invoke the `RetryScheduler` if a `DeadlockException` occurred to prevent a retry before all held locks have been released.

> **Note**
>
> Note that there is a clear distinction between a `ConcurrencyException` and a `ConflictingModificationException`. The first is used to indicate that a repository cannot save an aggregate, because the changes it contains were not applied to the latest available version. The latter indicates that the loaded aggregate contains changes that might not have been seen by the end-user. See [Advanced conflict detection and resolution](#advanced-conflict-detection-and-resolution) for more information.

This is a repository implementation that can store JPA compatible Aggregates. It is configured with an `EntityManager` to manage the actual persistence, and a class specifying the actual type of Aggregate stored in the Repository.

Event Sourcing repositories
===========================

Aggregate roots that implement the `EventSourcedAggregateRoot` interface can be stored in an event sourcing repository. Those repositories do not store the aggregate itself, but the series of events generated by the aggregate. Based on these events, the state of an aggregate can be restored at any time.

The `EventSourcingRepository` implementation provides the basic functionality needed by any event sourcing repository in the AxonFramework. It depends on an `EventStore` (see [Implementing your own Event Store](#implementing-your-own-eventstore)), which abstracts the actual storage mechanism for the events and an `AggregateFactory`, which is responsible for creating uninitialized aggregate instances.

The AggregateFactory specifies which aggregate needs to be created and how. Once an aggregate has been created, the `EventSourcingRepository` can initialize it using the Events it loaded from the Event Store. Axon Framework comes with a number of `AggregateFactory` implementations that you may use. If they do not suffice, it is very easy to create your own implementation.

*GenericAggregateFactory*

The `GenericAggregateFactory` is a special `AggregateFactory` implementation that can be used for any type of Event Sourced Aggregate Root. The `GenericAggregateFactory` creates an instance of the Aggregate type the repository manages. The Aggregate class must be non-abstract and declare a default no-arg constructor that does no initialization at all.

The GenericAggregateFactory is suitable for most scenarios where aggregates do not need special injection of non-serializable resources.

*SpringPrototypeAggregateFactory*

Depending on your architectural choices, it might be useful to inject dependencies into your aggregates using Spring. You could, for example, inject query repositories into your aggregate to ensure the existence (or nonexistence) of certain values.

To inject dependencies into your aggregates, you need to configure a prototype bean of your aggregate root in the Spring context that also defines the `SpringPrototypeAggregateFactory`. Instead of creating regular instances of using a constructor, it uses the Spring Application Context to instantiate your aggregates. This will also inject any dependencies in your aggregate.

*Implementing your own AggregateFactory*

In some cases, the `GenericAggregateFactory` just doesn't deliver what you need. For example, you could have an abstract aggregate type with multiple implementations for different scenarios (e.g. `PublicUserAccount` and `BackOfficeAccount` both extending an `Account`). Instead of creating different repositories for each of the aggregates, you could use a single repository, and configure an AggregateFactory that is aware of the different implementations.

The AggregateFactory must specify the aggregate type identifier. This is a String that the Event Store needs to figure out which events belong to which type of aggregate. Typically, this name is deducted from the abstract super-aggregate. In the given example that could be: Account.

The bulk of the work the Aggregate Factory does is creating uninitialized Aggregate instances. It must do so using a given aggregate identifier and the first Event from the stream. Usually, this Event is a creation event which contains hints about the expected type of aggregate. You can use this information to choose an implementation and invoke its constructor. Make sure no Events are applied by that constructor; the aggregate must be uninitialized.

Initializing aggregates based on the events can be a time-consuming effort, compared to the direct aggregate loading of the simple repository implementations. The `CachingEventSourcingRepository` provides a cache from which aggregates can be loaded if available. You can configure any JCache implementation with this repository. Note that this implementation can only use caching in combination with a pessimistic locking strategy.

> **Note**
>
> The Cache API has only been recently defined. As at the moment Axon was developed, the most recent version of the specification was not implemented, version 0.5 has been used. This API version is implemented by EhCache-JCache version "1.0.5-0.5". Axon 2.1 has been tested against this version.
>
> In a future version of Axon, the 1.0 version of the Cache API will be implemented, if the Cache providers have done that migration as well. Until then, you might have to select your cache implementation version carefully.

The `HybridJpaRepository` is a combination of the `GenericJpaRepository` and an Event Sourcing repository. It can only deal with event sourced aggregates, and stores them in a relational model as well as in an event store. When the repository reads an aggregate back in, it uses the relational model exclusively.

This repository removes the need for Event Upcasters (see [Event Upcasting](#event-upcasting)), making data migrations potentially easier. Since the aggregates are event sourced, you keep the ability to use the given-when-then test fixtures (see [Testing](8-testing.md#testing)). On the other hand, since it doesn't use the event store for reading, it doesn't allow for automated conflict resolution.

Event store implementations
===========================

Event Sourcing repositories need an event store to store and load events from aggregates. Typically, event stores are capable of storing events from multiple types of aggregates, but it is not a requirement.

Axon provides a number of implementations of event stores, all capable of storing all domain events (those raised from an Aggregate). These event stores use a `Serializer` to serialize and deserialize the event. By default, Axon provides some implementations of the Event Serializer that serializes events to XML: the `XStreamSerializer` and one that Serializes to JSON (using Jackson): `JacksonSerializer`.

`FileSystemEventStore`
----------------------

The `FileSystemEventStore` stores the events in a file on the file system. It provides good performance and easy configuration. The downside of this event store is that is does not provide transaction support and doesn't cluster very well. The only configuration needed is the location where the event store may store its files and the serializer to use to actually serialize and deserialize the events.

Note that the `FileSystemEventStore` is not aware of transactions and cannot automatically recover from crashes. Furthermore, it stores a single file for each aggregate, potentially creating too many files for the OS to handle. It is therefore not a suitable implementation for production environments.

> **Tip**
>
> When using the `FileSystemEventStore` in a test environment (or other environment where many aggregate may be created), you may end up with an "Out of disk space" error, even if there is plenty of *room* on the disk. The cause is that the filesystem runs out of i-nodes (or an equivalent when not on a Unix filesystem). This typically means that a filesystem holds too many files.
>
> To prevent this problem, make sure the output directory of the `FileSystemEventStore` is cleaned after each test run.

`JpaEventStore`
---------------

The `JpaEventStore` stores events in a JPA-compatible data source. Unlike the file system version, the `JPAEventStore` supports transactions. The JPA Event Store stores events in so called entries. These entries contain the serialized form of an event, as well as some fields where meta-data is stored for fast lookup of these entries. To use the `JpaEventStore`, you must have the JPA (`javax.persistence`) annotations on your classpath.

By default, the event store needs you to configure your persistence context (defined in `META-INF/persistence.xml` file) to contain the classes `DomainEventEntry` and `SnapshotEventEntry` (both in the `org.axonframework.eventsourcing.eventstore.jpa` package).

Below is an example configuration of a persistence context configuration:

``` xml
<persistence xmlns="http://java.sun.com/xml/ns/persistence" version="1.0">
    <persistence-unit name="eventStore" transaction-type="RESOURCE_LOCAL"> (1)
        <class>org...eventstore.jpa.DomainEventEntry</class> (2)
        <class>org...eventstore.jpa.SnapshotEventEntry</class>
    </persistence-unit>
</persistence>
```

1.   In this sample, there is a specific persistence unit for the event store. You may, however, choose to add the third line to any other persistence unit configuration.
1.   This line registers the `DomainEventEntry` (the class used by the `JpaEventStore`) with the persistence context.

> **Note**
>
> Axon uses Locking to prevent two threads from accessing the same Aggregate. However, if you have multiple JVMs on the same database, this won't help you. In that case, you'd have to rely on the database to detect conflicts. Concurrent access to the event store will result in a Key Constraint Violation, as the table only allows a single Event for an aggregate with any sequence number. Inserting a second event for an existing aggregate with an existing sequence number will result in an error.
>
> The JPA EventStore can detect this error and translate it to a `ConcurrencyException`. However, each database system reports this violation differently. If you register your `DataSource` with the `JpaEventStore`, it will try to detect the type of database and figure out which error codes represent a Key Constraint Violation. Alternatively, you may provide a `PersistenceExceptionTranslator` instance, which can tell if a given exception represents a Key Constraint Violation.
>
> If no `DataSource` or `PersistenceExceptionTranslator` is provided, exceptions from the database driver are thrown as-is.

By default, the JPA Event Store expects the application to have only a single, container managed, persistence context. In many cases, however, an application has more than one. In that case, you must provide an explicit `EntityManagerProvider` implementation that returns the `EntityManager` instance for the `EventStore` to use. This also allows for application managed persistence contexts to be used. It is the `EntityManagerProvider`'s responsibility to provide a correct instance of the `EntityManager`.

There are a few implementations of the `EntityManagerProvider` available, each for different needs. The `SimpleEntityManagerProvider` simply returns the `EntityManager` instance which is given to it at construction time. This makes the implementation a simple option for Container Managed Contexts. Alternatively, there is the `ContainerManagedEntityManagerProvider`, which returns the default persistence context, and is used by default by the Jpa Event Store.

If you have a persistence unit called "myPersistenceUnit" which you wish to use in the `JpaEventStore`, this is what the `EntityManagerProvider` implementation could look like:

``` java
public class MyEntityManagerProvider implements EntityManagerProvider {

    private EntityManager entityManager;

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @PersistenceContext(unitName = "myPersistenceUnit")
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }                
```

By default, the JPA Event Store stores entries in `DomainEventEntry` and `SnapshotEventEntry` entities. While this will suffice in many cases, you might encounter a situation where the meta-data provided by these entities is not enough. Or you might want to store events of different aggregate types in different tables.

If that is the case, you may provide your own implementation of `EventEntryStore` in the JPA Event Store's constructor. You will need to provide implementations of methods that load and store serialized events. Check the API Documentation of the `EventEntryStore` class for implementation requirements.

If you only want to change the table name or want to add some extra fields to the table, you can also create a class that extends from DefaultEventEntryStore, and override the `createDomainEventEntry` and/or `createSnapshotEventEntryMethod`. This method must return a `DomainEventEntry` and `SnapshotEventEntry` instance, respectively. By returning your own subclass of these, you can store different events in different tables, or add extra information in separate columns.

> **Warning**
>
> Note that persistence providers, such as Hibernate, use a first-level cache on their `EntityManager` implementation. Typically, this means that all entities used or returned in queries are attached to the `EntityManager`. They are only cleared when the surrounding transaction is committed or an explicit "clear" in performed inside the transaction. This is especially the case when the Queries are executed in the context of a transaction.
>
> To work around this issue, make sure to exclusively query for non-entity objects. You can use JPA's "SELECT new SomeClass(parameters) FROM ..." style queries to work around this issue. Alternatively, call `EntityManager.flush()` and `EntityManager.clear()` after fetching a batch of events. Failure to do so might result in `OutOfMemoryException`s when loading large streams of events.

JDBC Event Store
----------------

The JDBC event store uses a JDBC Connection to store Events in a JDBC compatible data storage. Typically, these are relational databases. Theoretically, anything that has a JDBC driver could be used to back the JDBC Event Store.

Similar to the JPA Event Store, the JDBC Event Store stores Events in entries. By default, each Event is stored in a single Entry, which corresponds with a row in a table. One table is used for Events and another for the Snapshots.

The `JdbcEventStore` can be configured with an `EventEntryStore` and a `Serializer`. The EventEntryStore defines how Events are appended to the event store. The serializer is used to convert the payload and meta data of an event into an array of bytes, ready for storage. In most cases, the DefaultEventEntryStore will suffice. It can be configured to accommodate all sort of different scenarios.

The `DefaultEventEntryStore` uses a `ConnectionProvider` to obtain connections. Typically, these connections can be obtained directly from a DataSource. However, Axon will bind these connections to a Unit of Work, so that a single connection is used in a Unit of Work. This ensures that a single transaction is used to store all events, even when multiple Units of Work are nested in the same thread.

> **Note**
>
> Spring users are recommended to use the namespace support to define a JDBC Event Store: `<axon:jdbc-event-store .../>` This will ensure that connections are bound to a transaction using the Platform Transaction Manager, if available.
>
> If you don't use namespace support, or define your own `ConnectionProvider`, you can use the `SpringDataSourceConnectionProvider` to attach a connection from a `DataSource` to an existing transaction. It is also recommended to wrap the `ConnectionProvider` in a `UnitOfWorkAwareConnectionProviderWrapper`, to ensure a single connection is used during the course of a single Unit of Work.

Most databases speak more or less the same language. However, there many so-called SQL Dialects. While the JDBC Event Store speaks a language all databases should be able to understand, it is possible that specific database vendors provide better performing alternatives to generic SQL commands. To accommodate those, the `DefaultEventEntryStore` works with a `EventSqlSchema`. The `EventSqlSchema` is an interface that prescribes a number of operations the `EventEntryStore` does on the underlying database. The `EventSqlSchema` is responsible for creating the correct `PreparedStatement`s for those. When you need to change a query that is executed against the database, it will usually suffice to override a single method in the `GenericEventSqlSchema`. The is, for example, a `PostgresEventSqlSchema` implementaion for use with a PostgreSQL database.

> **Warning**
>
> By default, Axon stores time stamps in the system timezone. However, many regions use daylight savings time, causing them to effectively change timezone throughout the year. This could cause events to be returned in a different order than how they were originally stored. It is recommended to store timestamps in the UTC timezone, or use the millis-since-epoch format.
>
> To force the JDBC Event Store to store dates in the UTC timezone, either configure Joda to generate all dates in UTC timezone, or tell the JDBC Event Store to convert all timestamps to UTC. This can be done by setting &lt;axon:jdbc-event-store ... force-utc-timestamp="true" ... /&gt;, or by calling `setForceUtc(true);` on the `GenericEventSqlSchema`.

MongoDB Event Store
-------------------

MongoDB is a document based NoSQL store. Its scalability characteristics make it suitable for use as an Event Store. Axon provides the `MongoEventStore`, which uses MongoDB as backing database. It is contained in the Axon Mongo module (Maven artifactId `axon-mongo`).

Events are stored in two separate collections: one for the actual event streams and one for the snapshots.

By default, the `MongoEventStore` stores each event in a separate document. It is, however, possible to change the `StorageStrategy` used. The alternative provided by Axon is the `DocumentPerCommitStorageStrategy`, which creates a single document for all Events that have been stored in a single commit (i.e. in the same `DomainEventStream`).

Storing an entire commit in a single document has the advantage that a commit is stored atomically. Furthermore, it requires only a single roundtrip for any number of events. A disadvantage is that it becomes harder to query events manually or through the `EventStoreManagement` methods. When refactoring the domain model, for example, it is harder to "transfer" events from one aggregate to another if they are included in a "commit document".

The MongoDB doesn't take a lot of configuration. All it needs is a reference to the collections to store the Events in, and you're set to go. For production environments, you may want to double check the indexes on your collections.

Event Store Utilities
---------------------

Axon provides a number of wrappers for Event Stores that may be useful in certain circumstances. For example, an environment may replay events up to a certain moment in time, in order to reproduce the state of the application at that moment.

The `TimestampCutoffReadonlyEventStore` is, as the name suggests, a read-only event store that only returns events older than a specific time. This allows you to reproduce state of an application at a specific time. This class is a wrapper around another event store (e.g. the one used in production).

The `SequenceEventStore` is a wrapper around two other Event Stores. When reading, it returns the events from both event stores. Appended events are only appended to the second event store. This is useful in cases where two different implementations of Event Stores are used for performance reasons, for example. The first would be a larger, but slower event store, while the second is optimized for quick reading and writing.

There is also an Event Store implementation that keeps te stored events in memory: the `VolatileEventStore`. While it probably outperforms any other event store out there, it is not really meant for long-term production use. However, it is very useful in short-lived tools or tests that require an event store.

Implementing your own event store
---------------------------------

If you have specific requirements for an event store, it is quite easy to implement one using different underlying data sources. Reading and appending events is done using a `DomainEventStream`, which is quite similar to iterator implementations.

Instead of eagerly deserializing Events, consider using the `SerializedDomainEventMessage`, which will postpone deserialization of Meta Data and Payload until it is actually used by a handler.

> **Tip**
>
> The `SimpleDomainEventStream` class will make the contents of a sequence ( `List` or `array`) of `EventMessage` instances accessible as event stream.

Influencing the serialization process
-------------------------------------

Event Stores need a way to serialize the Event to prepare it for storage. By default, Axon uses the `XStreamSerializer`, which uses [XStream](http://xstream.codehaus.org/) to serialize Events into XML. XStream is reasonably fast and is more flexible than Java Serialization. Furthermore, the result of XStream serialization is human readable. Quite useful for logging and debugging purposes.

The XStreamSerializer can be configured. You can define aliases it should use for certain packages, classes or even fields. Besides being a nice way to shorten potentially long names, aliases can also be used when class definitions of events change. For more information about aliases, visit the [XStream website](http://xstream.codehaus.org/).

Alternatively, Axon also provides the `JacksonSerializer`, which uses [Jackson](https://github.com/FasterXML/jackson) to serialize Events into JSON. While it produces a more compact serialized form, it does require that classes stick to the conventions (or configuration) required by Jackson.

> **Note**
>
> Configuring the serializer using Java code (or other JVM languages) is easy. However, configuring it in a Spring XML Application Context is not so trivial, due to its limitations to invoke methods. One of the options is to create a `FactoryBean` that creates an instance of an XStreamSerializer and configures it in code. Check the Spring Reference for more information.

You may also implement your own Serializer, simply by creating a class that implements `Serializer`, and configuring the Event Store to use that implementation instead of the default.

Event Upcasting
===============

Due to the ever-changing nature of software applications it is likely that event definitions also change over time. Since the Event Store is considered a read and append-only data source, your application must be able to read all events, regardless of when they have been added. This is where upcasting comes in.

Originally a concept of object-oriented programming, where "a subclass gets cast to its superclass automatically when needed", the concept of upcasting can also be applied to event sourcing. To upcast an event means to transform it from its original structure to its new structure. Unlike OOP upcasting, event upcasting cannot be done in full automation because the structure of the new event is unknown to the old event. Manually written Upcasters have to be provided to specify how to upcast the old structure to the new structure.

Upcasters are classes that take one input event of revision `x` and output zero or more new events of revision `x + 1`. Moreover, upcasters are processed in a chain, meaning that the output of one upcaster is sent to the input of the next. This allows you to update events in an incremental manner, writing an Upcaster for each new event revision, making them small, isolated, and easy to understand.

> **Note**
>
> Perhaps the greatest benefit of upcasting is that it allows you to do non-destructive refactoring, i.e. the complete event history remains intact.

In this section we'll explain how to write an upcaster, describe the two implementations of the Upcaster Chain that come with Axon, and explain how the serialized representations of events affects how upcasters are written.

To allow an upcaster to see what version of serialized object they are receiving, the Event Store stores a revision number as well as the fully qualified name of the Event. This revision number is generated by a `RevisionResolver`, configured in the serializer. Axon provides several implementations of the `RevisionResolver`, such as the `AnnotationRevisionResolver`, which checks for an `@Revision` annotation on the Event payload, a `SerialVersionUIDRevisionResolver` that uses the `serialVersionUID` as defined by Java Serialization API and a `FixedValueRevisionResolver`, which always returns a predefined value. The latter is useful when injecting the current application version. This will allow you to see which version of the application generated a specific event.

Maven users can use the `MavenArtifactRevisionResolver` to automatically use the project version. It is initialized using the groupId and artifactId of the project to obtain the version for. Since this only works in JAR files created by Maven, the version cannot always be resolved by an IDE. If a version cannot be resolved, `null` is returned.

Writing an upcaster
-------------------

To explain how to write an upcaster for Axon we'll walk through a small example, describing the details of writing an upcaster as we go along.

Let's assume that there is an Event Store containing many `AdministrativeDetailsUpdated` events. New requirements have let to the introduction of two new events: `AddressUpdatedEvent` and `InsurancePolicyUpdatedEvent`. Previously though, all information in these two events was contained in the old `AdministrativeDetailsUpdatedEvent`, which is now deprecated. To nicely handle this situation we'll write an upcaster to transform the `AdministrativeDetailsUpdatedEvent` into an `AddressUpdatedEvent` and an `InsurancePolicyUpdatedEvent`.

Here is the code for an upcaster: import org.dom4j.Document; public class AdministrativeDetailsUpdatedUpcaster implements Upcaster&lt;Document&gt; { @Override public boolean canUpcast(SerializedType serializedType) { return serializedType.getName().equals("org.example.AdministrativeDetailsUpdated") && "0".equals(serializedType.getRevision()); } @Override public Class&lt;Document&gt; expectedRepresentationType() { return Document.class; } @Override public List&lt;SerializedObject&lt;Document&gt;&gt; upcast(SerializedObject&lt;Document&gt; intermediateRepresentation, List&lt;SerializedType&gt; expectedTypes, UpcastingContext context) { Document administrativeDetailsUpdatedEvent = intermediateRepresentation.getData(); Document addressUpdatedEvent = new DOMDocument(new DOMElement("org.example.AddressUpdatedEvent")); addressUpdatedEvent.getRootElement() .add(administrativeDetailsUpdatedEvent.getRootElement().element("address").createCopy()); Document insurancePolicyUpdatedEvent = new DOMDocument(new DOMElement("org.example.InsurancePolicyUpdatedEvent").createCopy()); insurancePolicyUpdatedEvent.getRootElement() .add(administrativeDetailsUpdatedEvent.getRootElement().element("policy").createCopy()); List&lt;SerializedObject&lt;?&gt;&gt; upcastedEvents = new ArrayList&lt;SerializedObject&lt;?&gt;&gt;(); upcastedEvents.add(new SimpleSerializedObject&lt;Document&gt;( addressUpdatedEvent, Document.class, expectedTypes.get(0))); upcastedEvents.add(new SimpleSerializedObject&lt;Document&gt;( insurancePolicyUpdatedEvent, Document.class, expectedTypes.get(1))); return upcastedEvents; } @Override public List&lt;SerializedType&gt; upcast(SerializedType serializedType) { SerializedType addressUpdatedEventType = new SimpleSerializedType("org.example.AddressUpdatedEvent", "1"); SerializedType insurancePolicyUpdatedEventType = new SimpleSerializedType("org.example.InsurancePolicyUpdatedEvent", "1"); return Arrays.asList(addressUpdatedEventType, insurancePolicyUpdatedEventType); } } First we have to create a class that implements the `Upcaster` interface. Then we have to decide on which content type to work. For this example dom4j documents are used as they'll fit nicely with an event store that uses XML to store events. In Axon, Events have a revision, if the definition of an event changes, you should update its revision as well. The `canUpcast` method can then be used to check if an event needs to be upcasted. In the example we return true on the `canUpcast` method only when the incoming event has the type `org.example.AdministrativeDetailsUpdatedEvent` and has revision number 0. Axon will take care of calling the Upcaster's `upcast` method if the `canUpcast` method on that Upcaster returns true. Due to Java's type erasure we have to implement the `expectedRepresentationType` method to provide Axon with runtime type information on the content type by returning the `Class` object of the content type. The content type is used at runtime to determine how the incoming event needs to be converted to provide the upcaster with the event with the correct type. By copying the address and policy element from the `AdministrativeDetailsUpdatedEvent` each into its own document, the event is upcasted. Deserializing the result would get us an instance of the InsurancePolicyUpdatedEvent and an instance of the `AddressUpdatedEvent`. Upcasting can be expensive, possibly involving type conversion, deserialization and logic. Axon is smart enough to prevent this from happening when it is not necessary through the concept of `SerializedType`s. `SerializedType`s provide Axon with the information to delay event upcasting until the application requires it.

> **Note**
>
> In some occasions, it is necessary to upcast an event to one of multiple potential types, based on the contents of the event itself. This is the case where one historical event has been split into several new events, each one for a specific case. The Upcaster interface doesn't provide the event itself in the `upcast(SerializedType)` method.
>
> Alternatively, you may implement the `ExtendedUpcaster` interface. Unlike the `Upcaster` interface, this interface declares an `upcast(SerializedType, SerializedObject)` method. This method serves as a replacement for the `upcast(SerializedType)` method, for UpcasterChain implementations that support ExtendedUpcaster. The UpcasterChain implementations provided by Axon will always support this. Upcasters implementing `ExtendedUpcaster` may throw an `UnsupportedOperationException` in the `upcast(SerializedType)` method.

The Upcaster Chain
------------------

The Upcaster Chain is responsible for upcasting events by chaining the output of one upcaster to the next. It comes in the following two flavours:

-   The `SimpleUpcasterChain` immediately upcasts all events given to it and returns them.

-   The `LazyUpcasterChain` prepares the events to be upcasted but only upcasts the events that are actually used. Depending on whether or not your application needs all events, this can give you a significant performance benefit. In the worst case it's as slow as the `SimpleUpcasterChain`. The `LazyUpcasterChain` does not guarantee that all the events in an Event Stream are in fact upcasted. When your upcasters rely on information from previous events, this may be a problem.

The `LazyUpcasterChain` is a safe choice if your upcasters are stateless or do not depend on other upcasters. Always consider using the `LazyUpcasterChain` since it can provide a great performance benefit over the `SimpleUpcasterChain`. If you want guaranteed upcasting in a strict order, use the `SimpleUpcasterChain`.

Content type conversion
-----------------------

An upcaster works on a given content type (e.g. dom4j Document). To provide extra flexibility between upcasters, content types between chained upcasters may vary. Axon will try to convert between the content types automatically by using `ContentTypeConverter`s. It will search for the shortest path from type `x` to type `y`, perform the conversion and pass the converted value into the requested upcaster. For performance reasons, conversion will only be performed if the `canUpcast` method on the receiving upcaster yields true.

The `ContentTypeConverter`s may depend on the type of serializer used. Attempting to convert a `byte[]` to a dom4j `Document` will not make any sense unless a `Serializer` was used that writes an event as XML. To make sure the `UpcasterChain` has access to the serializer-specific `ContentTypeConverter`s, you can pass a reference to the serializer to the constructor of the `UpcasterChain`.

> **Tip**
>
> To achieve the best performance, ensure that all upcasters in the same chain (where one's output is another's input) work on the same content type.

If the content type conversion that you need is not provided by Axon you can always write one yourself using the `ContentTypeConverter` interface.

The `XStreamSerializer` supports Dom4J as well as XOM as XML document representations. The `JacksonSerializer` supports Jackson's `JsonNode`.

Snapshotting
============

When aggregates live for a long time, and their state constantly changes, they will generate a large amount of events. Having to load all these events in to rebuild an aggregate's state may have a big performance impact. The snapshot event is a domain event with a special purpose: it summarises an arbitrary amount of events into a single one. By regularly creating and storing a snapshot event, the event store does not have to return long lists of events. Just the last snapshot events and all events that occurred after the snapshot was made.

For example, items in stock tend to change quite often. Each time an item is sold, an event reduces the stock by one. Every time a shipment of new items comes in, the stock is incremented by some larger number. If you sell a hundred items each day, you will produce at least 100 events per day. After a few days, your system will spend too much time reading in all these events just to find out whether it should raise an "ItemOutOfStockEvent". A single snapshot event could replace a lot of these events, just by storing the current number of items in stock.

Creating a snapshot
-------------------

Snapshot creation can be triggered by a number of factors, for example the number of events created since the last snapshot, the time to initialize an aggregate exceeds a certain threshold, time-based, etc. Currently, Axon provides a mechanism that allows you to trigger snapshots based on an event count threshold.

The `EventCountSnapshotterTrigger` provides the mechanism to trigger snapshot creation when the number of events needed to load an aggregate exceeds a certain threshold. If the number of events needed to load an aggregate exceeds a certain configurable threshold, the trigger tells a `Snapshotter` to create a snapshot for the aggregate.

The snapshot trigger is configured on an Event Sourcing Repository and has a number of properties that allow you to tweak triggering:

-   `Snapshotter` sets the actual snapshotter instance, responsible for creating and storing the actual snapshot event;

-   `Trigger` sets the threshold at which to trigger snapshot creation;

-   `ClearCountersAfterAppend` indicates whether you want to clear counters when an aggregate is stored. The optimal setting of this parameter depends mainly on your caching strategy. If you do not use caching, there is no problem in removing event counts from memory. When an aggregate is loaded, the events are loaded in, and counted again. If you use a cache, however, you may lose track of counters. Defaults to `true` unless the `AggregateCache` or `AggregateCaches` is set, in which case it defaults to `false`.

-   `AggregateCache` and `AggregateCaches` allows you to register the cache or caches that you use to store aggregates in. The snapshotter trigger will register itself as a listener on the cache. If any aggregates are evicted, the snapshotter trigger will remove the counters. This optimizes memory usage in the case your application has many aggregates. Do note that the keys of the cache are expected to be the Aggregate Identifier.

A Snapshotter is responsible for the actual creation of a snapshot. Typically, snapshotting is a process that should disturb the operational processes as little as possible. Therefore, it is recommended to run the snapshotter in a different thread. The `Snapshotter` interface declares a single method: `scheduleSnapshot()`, which takes the aggregate's type and identifier as parameters.

Axon provides the `AggregateSnapshotter`, which creates and stores `AggregateSnapshot` instances. This is a special type of snapshot, since it contains the actual aggregate instance within it. The repositories provided by Axon are aware of this type of snapshot, and will extract the aggregate from it, instead of instantiating a new one. All events loaded after the snapshot events are streamed to the extracted aggregate instance.

> **Note**
>
> Do make sure that the `Serializer` instance you use (which defaults to the `XStreamSerializer`) is capable of serializing your aggregate. The `XStreamSerializer` requires you to use either a Hotspot JVM, or your aggregate must either have an accessible default constructor or implement the `Serializable` interface.

The `AbstractSnapshotter` provides a basic set of properties that allow you to tweak the way snapshots are created:

-   `EventStore` sets the event store that is used to load past events and store the snapshots. This event store must implement the `SnapshotEventStore` interface.

-   `Executor` sets the executor, such as a `ThreadPoolExecutor` that will provide the thread to process actual snapshot creation. By default, snapshots are created in the thread that calls the `scheduleSnapshot()` method, which is generally not recommended for production.

The `AggregateSnapshotter` provides on more property:

-   `AggregateFactories` is the property that allows you to set the factories that will create instances of your aggregates. Configuring multiple aggregate factories allows you to use a single Snapshotter to create snapshots for a variety of aggregate types. The `EventSourcingRepository` implementations provide access to the `AggregateFactory` they use. This can be used to configure the same aggregate factories in the Snapshotter as the ones used in the repositories.

> **Note**
>
> If you use an executor that executes snapshot creation in another thread, make sure you configure the correct transaction management for your underlying event store, if necessary. Spring users can use the `SpringAggregateSnapshotter`, which allows you to configure a `PlatformTransactionManager`. The `SpringAggregateSnapshotter` will autowire all aggregate factories (either directly, or via the `Repository`), if a list is not explicitly configured.

Storing Snapshot Events
-----------------------

All Axon-provided Event Store implementations are capable of storing snapshot events. They provide a special method that allows a `DomainEventMessage` to be stored as a snapshot event. You have to initialize the snapshot event completely, including the aggregate identifier and the sequence number. There is a special constructor on the `GenericDomainEventMessage` for this purpose. The sequence number must be equal to the sequence number of the last event that was included in the state that the snapshot represents. In most cases, you can use the `getVersion()` on the `AggregateRoot` (which each event sourced aggregate implements) to obtain the sequence number to use in the snapshot event.

When a snapshot is stored in the Event Store, it will automatically use that snapshot to summarize all prior events and return it in their place. All event store implementations allow for concurrent creation of snapshots. This means they allow snapshots to be stored while another process is adding Events for the same aggregate. This allows the snapshotting process to run as a separate process altogether.

> **Note**
>
> Normally, you can archive all events once they are part of a snapshot event. Snapshotted events will never be read in again by the event store in regular operational scenario's. However, if you want to be able to reconstruct aggregate state prior to the moment the snapshot was created, you must keep the events up to that date.

Axon provides a special type of snapshot event: the `AggregateSnapshot`, which stores an entire aggregate as a snapshot. The motivation is simple: your aggregate should only contain the state relevant to take business decisions. This is exactly the information you want captured in a snapshot. All Event Sourcing Repositories provided by Axon recognize the `AggregateSnapshot`, and will extract the aggregate from it. Beware that using this snapshot event requires that the event serialization mechanism needs to be able to serialize the aggregate.

Initializing an Aggregate based on a Snapshot Event
---------------------------------------------------

A snapshot event is an event like any other. That means a snapshot event is handled just like any other domain event. When using annotations to demarcate event handlers (`@EventHandler`), you can annotate a method that initializes full aggregate state based on a snapshot event. The code sample below shows how snapshot events are treated like any other domain event within the aggregate.

``` java
public class MyAggregate extends AbstractAnnotatedAggregateRoot {

    // ... code omitted for brevity

    @EventHandler
    protected void handleSomeStateChangeEvent(MyDomainEvent event) {
        // ...
    }

    @EventHandler
    protected void applySnapshot(MySnapshotEvent event) {
        // the snapshot event should contain all relevant state
        this.someState = event.someState;
        this.otherState = event.otherState;
    }
}                
```

There is one type of snapshot event that is treated differently: the `AggregateSnapshot`. This type of snapshot event contains the actual aggregate. The aggregate factory recognizes this type of event and extracts the aggregate from the snapshot. Then, all other events are re-applied to the extracted snapshot. That means aggregates never need to be able to deal with `AggregateSnapshot` instances themselves.

Pruning Snapshot Events
-----------------------

Once a snapshot event is written, it prevents older events and snapshot events from being read. Domain Events are still used in case a snapshot event becomes obsolete due to changes in the structure of an aggregate. The older snapshot events are hardly ever needed. `SnapshotEventStore` implementation may choose to keep only a limited amount of snapshots (e.g. only one) for each aggregate.

The `JpaEventStore` allows you to configure the amount of snapshots to keep per aggregate. It defaults to 1, meaning that only the latest snapshot event is kept for each aggregate. Use `setMaxSnapshotsArchived(int)` to change this setting. Use a negative integer to prevent pruning altogether.

Advanced conflict detection and resolution
==========================================

One of the major advantages of being explicit about the meaning of changes, is that you can detect conflicting changes with more precision. Typically, these conflicting changes occur when two users are acting on the same data (nearly) simultaneously. Imagine two users, both looking at a specific version of the data. They both decide to make a change to that data. They will both send a command like "on version X of this aggregate, do that", where X is the expected version of the aggregate. One of them will have the changes actually applied to the expected version. The other user won't.

Instead of simply rejecting all incoming commands when aggregates have been modified by another process, you could check whether the user's intent conflicts with any unseen changes. One way to do this, is to apply the command on the latest version of the aggregate, and check the generated events against the events that occurred since the version the user expected. For example, two users look at a Customer, which has version 4. One user notices a typo in the customer's address, and decides to fix it. Another user wants to register the fact that the customer moved to another address. If the fist user applied his command first, the second one will make the change to version 5, instead of the version 4 that he expected. This second command will generate a CustomerMovedEvent. This event is compared to all unseen events: AddressCorrectedEvent, in this case. A `ConflictResolver` will compare these events, and decide that these conflicts may be merged. If the other user had committed first, the `ConflictResolver` would have decided that a AddressCorrectedEvent on top of an unseen CustomerMovedEvent is considered a conflicting change.

Axon provides the necessary infrastructure to implement advanced conflict detection. By default, all repositories will throw a `ConflictingModificationException` when the version of a loaded aggregate is not equal to the expected version. Event Sourcing Repositories offer support for more advanced conflict detection, as described in the paragraph above.

To enable advanced conflict detection, configure a `ConflictResolver` on the `EventSourcingRepository`. This `ConflictResolver` is responsible for detecting conflicting modifications, based on the events representing these changes. Detecting these conflicts is a matter of comparing the two lists of `DomainEvent`s provided in the `resolveConflicts` method declared on the `ConflictResolver`. If such a conflict is found, a `ConflictingModificationException` (or better, a more explicit and explanatory subclass of it) must be thrown. If the `ConflictResolver` returns normally, the events are persisted, effectively meaning that the concurrent changes have been merged.
