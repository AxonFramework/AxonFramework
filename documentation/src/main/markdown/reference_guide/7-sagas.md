Managing complex business transactions
======================================

Not every command is able to completely execute in a single ACID transaction. A very common example that pops up quite often as an argument for transactions is the money transfer. It is often believed that an atomic and consistent transaction is absolutely required to transfer money from one account to another. Well, it's not. On the contrary, it is quite impossible to do. What if money is transferred from an account on Bank A, to another account on Bank B? Does Bank A acquire a lock in Bank B's database? If the transfer is in progress, is it strange that Bank A has deducted the amount, but Bank B hasn't deposited it yet? Not really, it's "underway". On the other hand, if something goes wrong while depositing the money on Bank B's account, Bank A's customer would want his money back. So we do expect some form of consistency, ultimately.

While ACID transactions are not necessary or even impossible in some cases, some form of transaction management is still required. Typically, these transactions are referred to as BASE transactions: **B**asic **A**vailability, **S**oft state, **E**ventual consistency. Contrary to ACID, BASE transactions cannot be easily rolled back. To roll back, compensating actions need to be taken to revert anything that has occurred as part of the transaction. In the money transfer example, a failure at Bank B to deposit the money, will refund the money in Bank A.

In CQRS, Sagas are responsible for managing these BASE transactions. They respond on Events produced by Commands and may produce new commands, invoke external applications, etc. In the context of Domain Driven Design, it is not uncommon for Sagas to be used as coordination mechanism between several bounded contexts.

Saga
====

A Saga is a special type of Event Listener: one that manages a business transaction. Some transactions could be running for days or even weeks, while others are completed within a few milliseconds. In Axon, each instance of a Saga is responsible for managing a single business transaction. That means a Saga maintains state necessary to manage that transaction, continuing it or taking compensating actions to roll back any actions already taken. Typically, and contrary to regular Event Listeners, a Saga has a starting point and an end, both triggered by Events. While the starting point of a Saga is usually very clear, there could be many ways for a Saga to end.

In Axon, all Sagas must implement the `Saga` interface. As with Aggregates, there is a Saga implementation that allows you to annotate event handling methods: the `AbstractAnnotatedSaga`.

Life Cycle
----------

A single Saga instance is responsible for managing a single transaction. That means you need to be able to indicate the start and end of a Saga's Life Cycle.

The `AbstractAnnotatedSaga` allows you to annotate Event Handlers with an annotation (`@SagaEventHandler`). If a specific Event signifies the start of a transaction, add another annotation to that same method: `@StartSaga`. This annotation will create a new saga and invoke its event handler method when a matching Event is published.

By default, a new Saga is only started if no suitable existing Saga (of the same type) can be found. You can also force the creation of a new Saga instance by setting the `forceNew` property on the `@StartSaga` annotation to `true`.

Ending a Saga can be done in two ways. If a certain Event always indicates the end of a Saga's life cycle, annotate that Event's handler on the Saga with `@EndSaga`. The Saga's Life Cycle will be ended after the invocation of the handler. Alternatively, you can call `end()` from inside the Saga to end the life cycle. This allows you to conditionally end the Saga.

> **Note**
>
> If you don't use annotation support, you need to properly configure your Saga Manager (see [???](#saga-manager) below). To end a Saga's life cycle, make sure the `isActive()` method of the Saga returns `false`.

Event Handling
--------------

Event Handling in a Saga is quite comparable to that of a regular Event Listener. The same rules for method and parameter resolution are valid here. There is one major difference, though. While there is a single instance of an Event Listener that deals with all incoming events, multiple instances of a Saga may exist, each interested in different Events. For example, a Saga that manages a transaction around an Order with Id "1" will not be interested in Events regarding Order "2", and vice versa.

Instead of publishing all Events to all Saga instances (which would be a complete waste of resources), Axon will only publish Events containing properties that the Saga has been associated with. This is done using `AssociationValue`s. An `AssociationValue` consists of a key and a value. The key represents the type of identifier used, for example "orderId" or "order". The value represents the corresponding value, "1" or "2" in the previous example.

The `@SagaEventHandler` annotation has two attributes, of which `associationProperty` is the most important one. This is the name of the property on the incoming Event that should be used to find associated Sagas. The key of the association value is the name of the property. The value is the value returned by property's getter method.

For example, consider an incoming Event with a method "`String getOrderId()`", which returns "123". If a method accepting this Event is annotated with `@SagaEventHandler(associationProperty="orderId")`, this Event is routed to all Sagas that have been associated with an `AssociationValue` with key "orderId" and value "123". This may either be exactly one, more than one, or even none at all.

Sometimes, the name of the property you want to associate with is not the name of the association you want to use. For example, you have a Saga that matches Sell orders against Buy orders, you could have a Transaction object that contains the "buyOrderId" and a "sellOrderId". If you want the saga to associate that value as "orderId", you can define a different keyName in the `@SagaEventHandler` annotation. It would then become `@SagaEventHandler(associationProperty="sellOrderId", keyName="orderId")`

When a Saga manages a transaction around one or more domain concepts, such as Order, Shipment, Invoice, etc, that Saga needs to be associated with instances of those concepts. An association requires two parameters: the key, which identifies the type of association (Order, Shipment, etc) and a value, which represents the identifier of that concept.

Associating a Saga with a concept is done in several ways. First of all, when a Saga is newly created when invoking a `@StartSaga` annotated Event Handler, it is automatically associated with the property identified in the `@SagaEventHandler` method. Any other association can be created using the `associateWith(String key, String/Number value)` method. Use the `removeAssociationWith(String key, String/Number value)` method to remove a specific association.

Imagine a Saga that has been created for a transaction around an Order. The Saga is automatically associated with the Order, as the method is annotated with `@StartSaga`. The Saga is responsible for creating an Invoice for that Order, and tell Shipping to create a Shipment for it. Once both the Shipment have arrived and the Invoice has been paid, the transaction is completed and the Saga is closed.

Here is the code for such a Saga:

``` java
public class OrderManagementSaga extends AbstractAnnotatedSaga {

    private boolean paid = false;
    private boolean delivered = false;
    private transient CommandGateway commandGateway;

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderCreatedEvent event) {
        // client generated identifiers
        ShippingId shipmentId = createShipmentId();
        InvoiceId invoiceId = createInvoiceId();
        // associate the Saga with these values, before sending the commands
        associateWith("shipmentId", shipmentId);
        associateWith("invoiceId", invoiceId);
        // send the commands
        commandGateway.send(new PrepareShippingCommand(...));
        commandGateway.send(new CreateInvoiceCommand(...));
    }

    @SagaEventHandler(associationProperty = "shipmentId")
    public void handle(ShippingArrivedEvent event) {
        delivered = true;
        if (paid) { end(); }
    }

    @SagaEventHandler(associationProperty = "invoiceId")
    public void handle(InvoicePaidEvent event) {
        paid = true;
        if (delivered) { end(); }
    }

    // ...
}
```
By allowing clients to generate an identifier, a Saga can be easily associated with a concept, without the need to a request-response type command. We associate the event with these concepts before publishing the command. This way, we are guaranteed to also catch events generated as part of this command. This will end this saga once the invoice is paid and the shipment has arrived.


Of course, this Saga implementation is far from complete. What should happen if the invoice is not paid in time. What if the shipment cannot be delivered? The Saga should be able to cope with those scenarios as well.

The order in which `@SagaEventHandler` annotated methods are evaluated is similar to that of `@EventHandler` methods (see [Annotated Event Handler](#annotated-event-handler)). However, unlike `@EventHandler` methods, you may have multiple `@SagaEventHandler` methods for the same event type. In that case, the number of parameters defines the evaluation order of the method, where the largest number of parameters comes first. A method matches if the parameters of the handler method match the incoming Event, and if the saga has an association with the property defined on the handler method. When two methods have the same number of arguments, the order is undefined.

In systems requiring auditing, or simply to be able to track the cause and effect of certain events, it is necessary to attach meta data headers to messages sent by Sagas. The `SagaManager` implementations allow you to configure an `AuditDataProvider`, which provides this information, based on an incoming EventMessage. Typically, the `AuditDataProvider` will copy certain headers from the incoming message. If this behavior is all you need, you can use the `SimpleCorrelationDataProvider`, which is configured with the names of the meta data entries to copy from the incoming Message.

When using the `EventTemplate` to send `Event`s, or the `CommandGateway` to send `Command`s, this correlation information is automatically attached to these messages. If you 'manually' place `EventMessage`s on the `EventBus`, or `CommandMessage`s on the `CommandBus`, you must attach the correlation data yourself. You can fetch the correlation data using `CorrelationDataHolder.getCorrelationData()`.

Keeping track of Deadlines
--------------------------

It is easy to make a Saga take action when something happens. After all, there is an Event to notify the Saga. But what if you want your Saga to do something when *nothing* happens? That's what deadlines are used for. In invoices, that's typically several weeks, while the confirmation of a credit card payment should occur within a few seconds.

In Axon, you can use an `EventScheduler` to schedule an Event for publication. In the example of an Invoice, you'd expect that invoice to be paid within 30 days. A Saga would, after sending the `CreateInvoiceCommand`, schedule an `InvoicePaymentDeadlineExpiredEvent` to be published in 30 days. The EventScheduler returns a `ScheduleToken` after scheduling an Event. This token can be used to cancel the schedule, for example when a payment of an Invoice has been received.

Axon provides two `EventScheduler` implementations: a pure Java one and one using Quartz 2 as a backing scheduling mechanism.

This pure-Java implementation of the `EventScheduler` uses a `ScheduledExecutorService` to schedule Event publication. Although the timing of this scheduler is very reliable, it is a pure in-memory implementation. Once the JVM is shut down, all schedules are lost. This makes this implementation unsuitable for long-term schedules.

The `SimpleEventScheduler` needs to be configured with an `EventBus` and a `SchedulingExecutorService` (see the static methods on the `java.util.concurrent.Executors` class for helper methods).

The `QuartzEventScheduler` is a more reliable and enterprise-worthy implementation. Using Quartz as underlying scheduling mechanism, it provides more powerful features, such as persistence, clustering and misfire management. This means Event publication is guaranteed. It might be a little late, but it will be published.

It needs to be configured with a Quartz `Scheduler` and an `EventBus`. Optionally, you may set the name of the group that Quartz jobs are scheduled in, which defaults to "AxonFramework-Events".

One or more components will be listening for scheduled Events. These components might rely on a Transaction being bound to the Thread that invokes them. Scheduled Events are published by Threads managed by the `EventScheduler`. To manage transactions on these threads, you can configure a `TransactionManager` or a `UnitOfWorkFactory` that creates a Transaction Bound Unit of Work.

> **Note**
>
> Spring users can use the `QuartzEventSchedulerFactoryBean` or `SimpleEventSchedulerFactoryBean` for easier configuration. It allows you to set the PlatformTransactionManager directly.

Injecting Resources
-------------------

Sagas generally do more than just maintaining state based on Events. They interact with external components. To do so, they need access to the Resources necessary to address to components. Usually, these resources aren't really part of the Saga's state and shouldn't be persisted as such. But once a Saga is reconstructed, these resources must be injected before an Event is routed to that instance.

For that purpose, there is the `ResourceInjector`. It is used by the `SagaRepository` (for existing Saga instances) and the `SagaFactory` (for newly created instances) to inject resources into a Saga. Axon provides a `SpringResourceInjector`, which injects annotated fields and methods with Resources from the Application Context, and a `SimpleResourceInjector`, which detects setters and injects resources which have been registered with it.

> **Tip**
>
> Since resources should not be persisted with the Saga, make sure to add the `transient` keyword to those fields. This will prevent the serialization mechanism to attempt to write the contents of these fields to the repository. The repository will automatically re-inject the required resources after a Saga has been deserialized.

The `SimpleResourceInjector` uses, as the name suggests, a simple mechanism to detect required resources. It scans the methods of a Saga to find one that starts with "set" and contains a single parameter, of a type that matches with of its known resources.

The `SimpleResourceInjector` is initialized with a collection of objects that a Saga may depend on. If a Saga has a setter method (name starts with "set" and has a single parameter) in which a resource can be passed, it will invoke that method.

The `SpringResourceInjector` uses Spring's dependency injection mechanism to inject resources into an aggregate. This means you can use setter injection or direct field injection if you require. The method or field to be injected needs to be annotated in order for Spring to recognize it as a dependency, for example with `@Autowired`.

Note that when you use the Axon namespace in Spring to create a Saga Repository or Saga Manager, the `SpringResourceInjector` is configured by default. For more information about wiring Sagas with Spring, see [Using Spring](9-using-spring.md#spring-sagas).

Saga Infrastructure
===================

Events need to be redirected to the appropriate Saga instances. To do so, some infrastructure classes are required. The most important components are the `SagaManager` and the `SagaRepository`.

Saga Manager
------------

The `SagaManager` is responsible for redirecting Events to the appropriate Saga instances and managing their life cycle. There are two `SagaManager` implementations in Axon Framework: the `AnnotatedSagaManager`, which provides the annotation support and the `SimpleSagaManager`, which is less powerful, but doesn't force you into using annotations.

Sagas operate in a highly concurrent environment. Multiple Events may reach a Saga at (nearly) the same time. This means that Sagas need to be thread safe. By default, Axon's `SagaManager` implementations will synchronize access to a Saga instance. This means that only one thread can access a Saga at a time, and all changes by one thread are guaranteed to be visible to any successive threads (a.k.a happens-before order in the Java Memory Model). Optionally, you may switch this locking off, if you are sure that your Saga is completely thread safe on its own. Just `setSynchronizeSagaAccess(false)`. When disabling synchronization, do take note of the fact that this will allow a Saga to be invoked while it is in the process of being stored by a repository. The result may be that a Saga is stored in an inconsistent state first, and overwritten by it's actual state later.

This is by far the least powerful of the two implementations, but it doesn't require the use of annotations. The `SimpleSagaManager` needs to be configured with a number of resources. Its constructor requires the type of Saga it manages, the `SagaRepository`, an `AssociationValueResolver`, a `SagaFactory` and the `EventBus`. The `AssociationValueResolver` is a component that returns a `Set` of `AssociationValue` for a given Event.

Then, you should also configure the types of Events the SagaManager should create new instances for. This is done through the `setEventsToAlwaysCreateNewSagasFor` and `setEventsToOptionallyCreateNewSagasFor` methods. They both accept a List of Event classes.

This `SagaManager` implementation uses annotations on the Sagas themselves to manage the routing and life cycle of that Saga. As a result, this manager allows all information about the life cycle of a Saga to be available inside the Saga class itself. It can also manage any number of saga types. That means only a single AnnotatedSagaManager is required, even if you have multiple types of Saga.

The `AnnotatedSagaManager` is constructed using a `SagaRepository`, a `SagaFactory` (optional) and a vararg array of `Saga` classes. If no `SagaFactory` is provided, a `GenericSagaFactory` is used. It assumes that all `Saga` classes have a public no-arg constructor.

If you use Spring, you can use the `axon` namespace to configure an `AnnotatedSagaManager`. The supported Saga types are provided as a comma separated list, or using component scanning by providing a package name. This will also automatically configure a `SpringResourceInjector`, which injects any annotated fields with resources from the Spring Application Context.

``` xml
<axon:saga-manager id="sagaManager" saga-factory="optionalSagaFactory"
                   saga-repository="sagaRepository" event-bus="eventBus">
    <axon:types>
        fully.qualified.ClassName,
        another.fq.ClassName
    </axon:types>
</axon:saga-manager>

<axon:saga-manager id="scanningSagaManager" base-package="package.to.scan"
                   saga-repository="sagaRepository" event-bus="eventBus">
    <axon:types>
       some.more.SagaClasses
    </axon:types>
</axon:saga-manager/>
```

As with Event Listeners, it is also possible to asynchronously handle events for sagas. To handle events asynchronously, the SagaManager needs to be configured with an `Executor` implementation. The `Executor` supplies the threads needed to process the events asynchronously. Often, you'll want to use a thread pool. You may, if you want, share this thread pool with other asynchronous activities.

When an executor is provided, the SagaManager will automatically use it to find associated Saga instances and dispatch the events to each of these instances. The SagaManager will guarantee that for each Saga instance, all events are processed in the order they arrive. For optimization purposes, this guarantee does not count in between Sagas.

Because Transactions are often Thread bound, you may need to configure a Transaction Manager with the SagaManager. This transaction manager is invoked before and after each invocation to the Saga Repository and before and after each batch of Events has been processed by the Saga itself.

In a Spring application context, a Saga Manager can be marked as asynchronous by adding the `executor` and optionally the `transaction-manager` attributes to the `saga-manager` element, as shown below. The `processor-count` attribute defines the number of threads that should process the sagas.

``` xml
<axon:saga-manager id="sagaManager" saga-factory="optionalSagaFactory"
                   saga-repository="sagaRepository" event-bus="eventBus">
    <axon:async processor-count="10" executor="myThreadPool" transaction-manager="txManager"/>
    <axon:types>
        fully.qualified.ClassName,
        another.fq.ClassName
    </axon:types>
</axon:saga-manager>
```

The transaction-manager should point to a `PlatformTransactionManager`, Spring's interface for transaction managers. Generally you can use the same transaction manager as the other components in your application (e.g. `JpaTransactionManager`).

> **Note**
>
> Since the processing is asynchronous, Exceptions cannot be propagated to the components publishing the Event. Exceptions raised by Sagas while handling an Event are logged and discarded. When an exception occurs while persisting Saga state, the SagaManager will retry persisting the Saga at a later stage. If there are incoming Events, they are processed using the in-memory representation, re-attempting the persistence later on.
>
> When shutting down the application, the SagaManager will do a final attempt to persist the Sagas to their repository, and give up if it does not succeed, allowing the application to finish the shutdown process.

Saga Repository
---------------

The `SagaRepository` is responsible for storing and retrieving Sagas, for use by the `SagaManager`. It is capable of retrieving specific Saga instances by their identifier as well as by their Association Values.

There are some special requirements, however. Since concurrency handling in Sagas is a very delicate procedure, the repository must ensure that for each conceptual Saga instance (with equal identifier) only a single instance exists in the JVM.

Axon provides three `SagaRepository` implementations: the `InMemorySagaRepository`, the `JpaSagaRepository` and the `MongoSagaRepository`.

As the name suggests, this repository keeps a collection of Sagas in memory. This is the simplest repository to configure and the fastest to use. However, it doesn't provide any persistence. If the JVM is shut down, any stored Saga is lost. This implementation is particularly suitable for testing and some very specialized use cases.

The `JpaSagaRepository` uses JPA to store the state and Association Values of Sagas. Sagas do not need any JPA annotations; Axon will serialize the sagas using a `Serializer` (similar to Event serialization, you can use either a `JavaSerializer` or an `XStreamSerializer`).

The JpaSagaRepository is configured with a JPA `EntityManager`, a `ResourceInjector` and a `Serializer`. Optionally, you can choose whether to explicitly flush the `EntityManager` after each operation. This will ensure that data is sent to the database, even before a transaction is committed. the default is to use explicit flushes.

The `JdbcSagaRepository` uses plain JDBC to store stage instances and their association values. Similar to the `JpaSagaRepository`, Saga instances don't need to be aware of how they are stored. They are serialized using a Serializer.

The `JdbcSagaRepository` is configured with a `ConnectionProvider`, which can simply be a `DataSource` wrapped in a `DataSourceConnectionProvider`, a `ResourceInjector` and a `Serializer`. While not required, it is recommended to use a `UnitOfWorkAwareConnectionProviderWrapper`. It will check the current Unit of Work for an already open database connection. This will ensure that all activity within a unit of work is done on a single connection.

Unlike JPA, the JdbcSagaRepository uses plain SQL statement to store and retrieve information. This may mean that some operations depend on the Database specific SQL dialect. It may also be the case that certain Database vendors provide non-standard features that you would like to use. To allow for this, you can provide your own `SagaSqlSchema`. The `SagaSqlSchema` is an interface that defines all the operations the repository needs to perform on the underlying database. It allows you to customize the SQL statement executed for each one of them. The default is the `GenericSagaSqlSchema`. Other implementations available are `PostgresSagaSqlSchema` and `HsqlSagaSchema`.

Similar to the `JpaSagaRepository`, the `MongoSagaRepository` stores the Saga instances and their associations in a database. The `MongoSagaRepository` stores sagas in a single Collection in a MongoDB database. Per Saga instance, a single document is created.

The MongoSagaRepository also ensures that at any time, only a single Saga instance exists for any unique Saga in a single JVM. This ensures that no state changes are lost due to concurrency issues.

Caching
-------

If a database backed Saga Repository is used, saving and loading Saga instances may be a relatively expensive operation. Especially in situations where the same Saga instance is invoked multiple times within a short timespan, a cache can be beneficial to the application's performance.

Axon provides the `CachingSagaRepository` implementation. It is a Saga Repository that wraps another repository, which does the actual storage. When loading Sagas or Association Values, the `CachingSagaRepository` will first consult its caches, before delegating to the wrapped repository. When storing information, all call are always delegated, to ensure that the backing storage always has a consistent view on the Saga's state.

To configure caching, simply wrap any Saga repository in a `CachingSagaRepository`. The constructor of the CachingSagaRepository takes three parameters: the repository to wrap and the caches to use for the Association Values and Saga instances, respectively. The latter two arguments may refer to the same cache, or to different ones. This depends on the eviction requirements of your specific application.

> **Note**
>
> The Cache API has only been recently defined. As at the moment Axon was developed, the most recent version of the specification was not implemented, version 0.5 has been used. This API version is implemented by EhCache-JCache version "1.0.5-0.5". Axon 2.1 has been tested against this version.
>
> In a future version of Axon, the 1.0 version of the Cache API will be implemented, if the Cache providers have done that migration as well. Until then, you might have to select your cache implementation version carefully.

Spring users can use the `<axon:cache-config>` element to add caching behavior to a `<axon:jpa-saga-repository>` element. It is configured with two cache references (which may refer to the same cache): one which stores the cached Saga instances, and one that stores the associations.

``` xml
<axon:jpa-saga-repository id="cachingSagaRepository">
    <axon:cache-config saga-cache="sagaCacheRef" associations-cache="associationCacheRef"/>
</axon:jpa-saga-repository>
```
