Command Handling
================

A state change within an application starts with a Command. A Command is a combination of expressed intent (which describes what you want done) as well as the information required to undertake action based on that intent. A Command Handler is responsible for handling commands of a certain type and taking action based on the information contained inside it.

The use of an explicit command dispatching mechanism has a number of advantages. First of all, there is a single object that clearly describes the intent of the client. By logging the command, you store both the intent and related data for future reference. Command handling also makes it easy to expose your command processing components to remote clients, via web services for example. Testing also becomes a lot easier, you could define test scripts by just defining the starting situation (given), command to execute (when) and expected results (then) by listing a number of events and commands (see [Testing](8-testing.md#testing)). The last major advantage is that it is very easy to switch between synchronous and asynchronous command processing.

This doesn't mean Command dispatching using explicit command object is the only right way to do it. The goal of Axon is not to prescribe a specific way of working, but to support you doing it your way. It is still possible to use a Service layer that you can invoke to execute commands. The method will just need to start a unit of work (see [Unit of Work](#unit-of-work)) and perform a commit or rollback on it when the method is finished.

The next sections provide an overview of the tasks related to creating a Command Handling infrastructure with the Axon Framework.

The Command Gateway
===================

The Command Gateway is a convenient interface towards the Command dispatching mechanism. While you are not required to use a Gateway to dispatch Commands, it is generally the easiest option to do so.

There are two ways to use a Command Gateway. The first is to use the `CommandGateway` interface and `DefaultCommandGateway` implementation provided by Axon. The command gateway provides a number of methods that allow you to send a command and wait for a result either synchronously, with a timeout or asynchronously.

The other option is perhaps the most flexible of all. You can turn almost any interface into a Command Gateway using the `GatewayProxyFactory`. This allows you to define your application's interface using strong typing and declaring your own (checked) business exceptions. Axon will automatically generate an implementation for that interface at runtime.

Configuring the Command Gateway
-------------------------------

Both your custom gateway and the one provided by Axon need to be configured with at least access to the Command Bus. In addition, the Command Gateway can be configured with a `RetryScheduler`, `CommandDispatchInterceptor`s, `CommandCallback`s and `CorrelationDataResolver`s.

The `RetryScheduler` is capable of scheduling retries when command execution has failed. The `IntervalRetryScheduler` is an implementation that will retry a given command at set intervals until it succeeds, or a maximum number of retries is done. When a command fails due to an exception that is explicitly non-transient, no retries are done at all. Note that the retry scheduler is only invoked when a command fails due to a `RuntimeException`. Checked exceptions are regarded "business exception" and will never trigger a retry.

`CommandDispatchInterceptor`s allow modification of `CommandMessage`s prior to dispatching them to the Command Bus. In contrast to `CommandDispatchInterceptor`s configured on the CommandBus, these interceptors are only invoked when messages are sent through this gateway. The interceptors can be used to attach meta data to a command or do validation, for example.

The `CommandCallback`s are invoked for each command sent. This allows for some generic behavior for all Commands sent through this gateway, regardless of their type.

Creating a Custom Command Gateway
---------------------------------

The `GatewayProxyFactory` creates an instance of a Command Gateway based on an interface class. The behavior of each method is based on the parameter types, return type and declared exception. Using this gateway is not only convenient, it makes testing a lot easier by allowing you to mock your interface where needed.

This is how parameter affect the behavior of the CommandGateway:

-   The first parameter is expected to be the actual command object to dispatch.

-   Parameters annotated with `@MetaData` will have their value assigned to the meta data field with the identifier passed as annotation parameter

-   Parameters of type `MetaData` will be merged with the `MetaData` on the CommandMessage. Meta data defined by latter parameters will overwrite the meta data of earlier parameters, if their key is equal.

-   Parameters of type `CommandCallback` will have their `onSuccess` or `onFailure` invoked after the Command is handled. You may pass in more than one callback, and it may be combined with a return value. In that case, the invocations of the callback will always match with the return value (or exception).

-   The last two parameters may be of types `long` (or `int`) and `TimeUnit`. In that case the method will block at most as long as these parameters indicate. How the method reacts on a timeout depends on the exceptions declared on the method (see below). Note that if other properties of the method prevent blocking altogether, a timeout will never occur.

The declared return value of a method will also affect its behavior:

-   A `void` return type will cause the method to return immediately, unless there are other indications on the method that one would want to wait, such as a timeout or declared exceptions.

-   A Future return type will cause the method to return immediately. You can access the result of the Command Handler using the Future instance returned from the method. Exceptions and timeouts declared on the method are ignored.

-   Any other return type will cause the method to block until a result is available. The result is cast to the return type (causing a ClassCastException if the types don't match).

Exceptions have the following effect:

-   Any declared checked exception will be thrown if the Command Handler (or an interceptor) threw an exceptions of that type. If a checked exception is thrown that has not been declared, it is wrapped in a `CommandExecutionException`, which is a `RuntimeException`.

-   When a timeout occurs, the default behavior is to return `null` from the method. This can be changed by declaring a `TimeoutException`. If this exception is declared, a `TimeoutException` is thrown instead.

-   When a Thread is interrupted while waiting for a result, the default behavior is to return null. In that case, the interrupted flag is set back on the Thread. By declaring an `InterruptedException` on the method, this behavior is changed to throw that exception instead. The interrupt flag is removed when the exception is thrown, consistent with the java specification.

-   Other Runtime Exceptions may be declared on the method, but will not have any effect other than clarification to the API user.

Finally, there is the possibility to use annotations:

-   As specified in the parameter section, the `@MetaData` annotation on a parameter will have the value of that parameter added as meta data value. The key of the meta data entry is provided as parameter to the annotation.

-   Methods annotated with `@Timeout` will block at most the indicated amount of time. This annotation is ignored if the method declares timeout parameters.

-   Classes annotated with `@Timeout` will cause all methods declared in that class to block at most the indicated amount of time, unless they are annotated with their own `@Timeout` annotation or specify timeout parameters.

``` java
public interface MyGateway {

    // fire and forget
    void sendCommand(MyPayloadType command);

    // method that attaches meta data and will wait for a result for 10 seconds
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    ReturnValue sendCommandAndWaitForAResult(MyPayloadType command,
                                             @MetaData("userId") String userId);

    // alternative that throws exceptions on timeout
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    ReturnValue sendCommandAndWaitForAResult(MyPayloadType command)
                         throws TimeoutException, InterruptedException;

    // this method will also wait, caller decides how long
    void sendCommandAndWait(MyPayloadType command, long timeout, TimeUnit unit)
                         throws TimeoutException, InterruptedException;
}

// To create an instance:
GatewayProxyFactory factory = new GatewayProxyFactory(commandBus);
MyGateway myGateway = factory.createGateway(MyGateway.class);
```

When using Spring, the easiest way to create a custom Command Gateway is by using the `CommandGatewayFactoryBean`. It uses setter injection, making it easier to configure. Only the "commandBus" property is mandatory.

    <bean id="myGateway" class="org.axonframework.commandhandling.gateway.CommandGatewayFactoryBean">
        <property name="commandBus" ref="commandBus"/>
        <property name="gatewayInterface" value="package.to.MyGateway"/>
    </bean>

The Command Bus
===============

The Command Bus is the mechanism that dispatches commands to their respective Command Handlers. Each Command is always sent to the exactly one command handler. If no command handler is available for the dispatched command, an `NoHandlerForCommandException` exception is thrown. Subscribing multiple command handlers to the same command type will result in subscriptions replacing each other. In that case, the last subscription wins.

Dispatching commands
--------------------

The CommandBus provides two methods to dispatch commands to their respective handler: `dispatch(commandMessage, callback)` and `dispatch(commandMessage)`. The first parameter is a message containing the actual command to dispatch. The optional second parameter takes a callback that allows the dispatching component to be notified when command handling is completed. This callback has two methods: `onSuccess()` and `onFailure()`, which are called when command handling returned normally, or when it threw an exception, respectively.

The calling component may not assume that the callback is invoked in the same thread that dispatched the command. If the calling thread depends on the result before continuing, you can use the `FutureCallback`. It is a combination of a `Future` (as defined in the java.concurrent package) and Axon's `CommandCallback`. Alternatively, consider using a Command Gateway.

Best scalability is achieved when your application is not interested in the result of a dispatched command at all. In that case, you should use the single-parameter version of the `dispatch` method. If the `CommandBus` is fully asynchronous, it will return immediately after the command has been successfully dispatched. Your application will just have to guarantee that the command is processed and with "positive outcome", sooner or later...

SimpleCommandBus
----------------

The `SimpleCommandBus` is, as the name suggests, the simplest implementation. It does straightforward processing of commands in the thread that dispatches them. After a command is processed, the modified aggregate(s) are saved and generated events are published in that same thread. In most scenario's, such as web applications, this implementation will suit your needs.

The `SimpleCommandBus` allows interceptors to be configured. `CommandDispatchInterceptor`s are invoked when a command is dispatched on the Command Bus. The `CommandHandlerInterceptor`s are invoked before the actual command handler method is, allowing you to do modify or block the command. See [Command Handler Interceptors](#command-handler-interceptors) for more information.

The `SimpleCommandBus` maintains a Unit of Work for each command published. This Unit of Work is created by a factory implementing the `UnitOfWorkFactory` interface. To suit any specific needs your application might have, you can supply your own factory to change the Unit of Work implementation used.

Since all command processing is done in the same thread, this implementation is limited to the JVM's boundaries. The performance of this implementation is good, but not extraordinary. To cross JVM boundaries, or to get the most out of your CPU cycles, check out the other `CommandBus` implementations.

DisruptorCommandBus
-------------------

The `SimpleCommandBus` has reasonable performance characteristics, especially when you've gone through the performance tips in [Performance Tuning](10-performance-tuning.md#performance-tuning). The fact that the `SimpleCommandBus` needs locking to prevent multiple threads from concurrently accessing the same aggregate causes processing overhead and lock contention.

The `DisruptorCommandBus` takes a different approach to multithreaded processing. Instead of having multiple threads each doing the same process, there are multiple threads, each taking care of a piece of the process. The `DisruptorCommandBus` uses the Disruptor (<http://lmax-exchange.github.io/disruptor/>), a small framework for concurrent programming, to achieve much better performance, by just taking a different approach to multithreading. Instead of doing the processing in the calling thread, the tasks are handed off to two groups of threads, that each take care of a part of the processing. The first group of threads will execute the command handler, changing an aggregate's state. The second group will store and publish the events to the Event Store and Event Bus.

While the `DisruptorCommandBus` easily outperforms the `SimpleCommandBus` by a factor of 4(!), there are a few limitations:

-   The `DisruptorCommandBus` only supports Event Sourced Aggregates. This Command Bus also acts as a Repository for the aggregates processed by the Disruptor. To get a reference to the Repository, use `createRepository(AggregateFactory)`.

-   A Command can only result in a state change in a single aggregate instance.

-   When using a Cache, it allows only a single aggregate for a given identifier. This means it is not possible to have two aggregates of different types with the same identifier.

-   Commands should generally not cause a failure that requires a rollback of the Unit of Work. When a rollback occurs, the `DisruptorCommandBus` cannot guarantee that Commands are processed in the order they were dispatched. Furthermore, it requires a retry of a number of other commands, causing unnecessary computations.

-   When creating a new Aggregate Instance, commands updating that created instance may not all happen in the exact order as provided. Once the aggregate is created, all commands will be executed exactly in the order they were dispatched. To ensure the order, use a callback on the creating command to wait for the aggregate being created. It shouldn't take more than a few milliseconds.

To construct a `DisruptorCommandBus` instance, you need an `AggregateFactory`, an `EventBus` and `EventStore`. These components are explained in [Repositories and Event Stores](5-repositories-and-event-stores.md) and [Event Bus](6-event-listeners.md#event-bus). Finally, you also need a `CommandTargetResolver`. This is a mechanism that tells the disruptor which aggregate is the target of a specific Command. There are two implementations provided by Axon: the `AnnotationCommandTargetResolver`, which uses annotations to describe the target, or the `MetaDataCommandTargetResolver`, which uses the Command's Meta Data fields.

Optionally, you can provide a `DisruptorConfiguration` instance, which allows you to tweak the configuration to optimize performance for your specific environment. Spring users can use the &lt;axon:disruptor-command-bus&gt; element for easier configuration of the `DisruptorCommandBus`.

-   Buffer size: The number of slots on the ring buffer to register incoming commands. Higher values may increase throughput, but also cause higher latency. Must always be a power of 2. Defaults to 4096.

-   ProducerType: Indicates whether the entries are produced by a single thread, or multiple. Defaults to multiple.

-   WaitStrategy: The strategy to use when the processor threads (the three threads taking care of the actual processing) need to wait for each other. The best WaitStrategy depends on the number of cores available in the machine, and the number of other processes running. If low latency is crucial, and the DisruptorCommandBus may claim cores for itself, you can use the `BusySpinWaitStrategy`. To make the Command Bus claim less of the CPU and allow other threads to do processing, use the `YieldingWaitStrategy`. Finally, you can use the `SleepingWaitStrategy` and `BlockingWaitStrategy` to allow other processes a fair share of CPU. The latter is suitable if the Command Bus is not expected to be processing full-time. Defaults to the `BlockingWaitStrategy`.

-   Executor: Sets the Executor that provides the Threads for the `DisruptorCommandBus`. This executor must be able to provide at least 4 threads. 3 of the threads are claimed by the processing components of the `DisruptorCommandBus`. Extra threads are used to invoke callbacks and to schedule retries in case an Aggregate's state is detected to be corrupt. Defaults to a `CachedThreadPool` that provides threads from a thread group called "DisruptorCommandBus".

-   TransactionManager: Defines the Transaction Manager that should ensure that the storage and publication of events are executed transactionally.

-   InvokerInterceptors: Defines the `CommandHandlerInterceptor`s that are to be used in the invocation process. This is the process that calls the actual Command Handler method.

-   PublisherInterceptors: Defines the `CommandHandlerInterceptor`s that are to be used in the publication process. This is the process that stores and publishes the generated events.

-   RollbackConfiguration: Defines on which Exceptions a Unit of Work should be rolled back. Defaults to a configuration that rolls back on unchecked exceptions.

-   RescheduleCommandsOnCorruptState: Indicates whether Commands that have been executed against an Aggregate that has been corrupted (e.g. because a Unit of Work was rolled back) should be rescheduled. If `false` the callback's `onFailure()` method will be invoked. If `true` (the default), the command will be rescheduled instead.

-   CoolingDownPeriod: Sets the number of seconds to wait to make sure all commands are processed. During the cooling down period, no new commands are accepted, but existing commands are processed, and rescheduled when necessary. The cooling down period ensures that threads are available for rescheduling the commands and calling callbacks. Defaults to 1000 (1 second).

-   Cache: Sets the cache that stores aggregate instances that have been reconstructed from the Event Store. The cache is used to store aggregate instances that are not in active use by the disruptor.

-   InvokerThreadCount: The number of threads to assign to the invocation of command handlers. A good starting point is half the number of cores in the machine.

-   PublisherThreadCount: The number of threads to use to publish events. A good starting point is half the number of cores, and could be increased if a lot of time is spent on IO.

-   SerializerThreadCount: The number of threads to use to pre-serialize events. This defaults to 1, but is ignored if no serializer is configured.

-   Serializer: The serializer to perform pre-serialization with. When a serializer is configured, the `DisruptorCommandBus` will wrap all generated events in a `SerializationAware` message. The serialized form of the payload and meta data is attached before they are published to the Event Store or Event Bus. This can drastically improve performance when the same serializer is used to store and publish events to a remote destination.

Command Handlers
================

The Command Handler is the object that receives a Command of a pre-defined type and takes action based on its contents. In Axon, a Command may be any object. There is no predefined type that needs to be implemented.

Creating a Command Handler
--------------------------

A Command Handler must implement the `CommandHandler` interface. This interface declares only a single method: `Object handle(CommandMessage<T>
                    command, UnitOfWork uow)`, where T is the type of Command this Handler can process. The concept of the UnitOfWork is explained in [Unit of Work](#unit-of-work). Be weary when using return values. Typically, it is a bad idea to use return values to return server-generated identifiers. Consider using client-generated (random) identifiers, such as UUIDs. They allow for a "fire and forget" style of command handlers, where a client does not have to wait for a response. As return value in such a case, you are recommended to simply return `null`.

Subscribing to a Command Bus
----------------------------

You can subscribe and unsubscribe command handlers using the `subscribe` and `unsubscribe` methods on `CommandBus`, respectively. They both take two parameters: the type of command to (un)subscribe the handler to, and the handler to (un)subscribe. An unsubscription will only be successful if the handler passed as the second parameter was currently assigned to handle that type of command. If another command was subscribed to that type of command, nothing happens.

``` java
CommandBus commandBus = new SimpleCommandBus();

// to subscribe a handler:
commandBus.subscribe(MyPayloadType.class.getName(), myCommandHandler);

// we can subscribe the same handler to different command types
commandBus.subscribe(MyOtherPayload.class.getName(), myCommandHandler);

// we can also unsubscribe the handler from one of these types:
commandBus.unsubscribe(MyOtherPayload.class.getName(), myCommandHandler);

// we don't have to use the payload to identifier the command type (but it's a good default)
commandBus.subscribe("MyCustomCommand", myCommandHandler);
```

Annotation based handlers
-------------------------

More often than not, a command handler will need to process several types of closely related commands. With Axon's annotation support you can use any POJO as command handler. Just add the `@CommandHandler` annotation to your methods to turn them into a command handler. These methods should declare the command to process as the first parameter. They may take optional extra parameters, such as the `UnitOfWork` for that command (see [Unit of Work](#unit-of-work)). Note that for each command type, there may only be one handler! This restriction counts for all handlers registered to the same command bus.

You can use the `AnnotationCommandHandlerAdapter` to turn your annotated class into a `CommandHandler`. To register the adapter to the Command Bus, you can use the `supportedCommands()` method to obtain the set of command types the adapter can handle. Register the adapter with the command bus for each of those commands.

The `AnnotationCommandHandlerAdapter`, as well as the `AggregateCommandHandlerAdapter`, use `ParameterResolver`s to resolve the value that should be passed in the parameters of methods annotated with `@CommandHandler`. By default, Axon uses an instance that allows you to use the following parameter types:

-   The first parameter is always the payload of the Command Message. It may also be of type `Message` or `CommandMessage`, if the `@CommandHandler` annotation explicitly defined the name of the Command the handler can process. By default, a Command name is the fully qualified class name of the Command's payload.

-   Parameters annotated with `@MetaData` will resolve to the Meta Data value with the key as indicated on the annotation. If `required` is `false` (default), `null` is passed when the meta data value is not present. If `required` is `true`, the resolver will not match and prevent the method from being invoked when the meta data value is not present.

-   Parameters of type `MetaData` will have the entire `MetaData` of a `CommandMessage` injected.

-   Parameters of type `UnitOfWork` get the current Unit of Work injected. This allows command handlers to register actions to be performed at specific stages of the Unit of Work, or gain access to the resources registered with it.

-   When using Spring and `<axon:annotation-config/>` is declared, any other parameters will resolve to autowired beans, if exactly one injectable candidate is available in the application context. This allows you to inject resources directly into `@CommandHandler` annotated methods.

``` java
public class MyAnnotatedHandler {

    @CommandHandler
    public void handleSomeCommand(SomeCommand command, @MetaData("userId") String userId) {
        // whatever logic here
    }

    @CommandHandler(commandName = "myCustomCommand")
    public void handleCustomCommand(SomeCommand command) {
       // handling logic here
    }

}

// To register the annotated handlers to the command bus:
AnnotationCommandHandlerAdapter handler = new AnnotationCommandHandlerAdapter(new MyAnnotatedHandler());
for (String supportedCommand : handler.supportedCommands()) {
    commandBus.subscribe(supportedCommand, handler);
}

// and to unsubscribe again:
for (String supportedCommand : handler.supportedCommands()) {
    commandBus.unsubscribe(supportedCommand, handler);
}
```

You can configure additional `ParameterResolver`s by extending the `ParameterResolverFactory` class and creating a file named `/META-INF/service/org.axonframework.common.annotation.ParameterResolverFactory` containing the fully qualified name of the implementing class.

> **Note**
>
> If you use Spring, you can add the `<axon:annotation-config/>` element to your application context. It will turn any bean with `@CommandHandler` annotated methods into a command handler. They will also be automatically subscribed to the `CommandBus`. In combination with Spring's classpath scanning (`@Component`), this will automatically subscribe any command handler in your application.

> **Caution**
>
> At this moment, OSGi support is limited to the fact that the required headers are mentioned in the manifest file. The automatic detection of `ParameterResolverFactory` instances works in OSGi, but due to classloader limitations, it might be necessary to copy the contents of the `/META-INF/service/org.axonframework.common.annotation.ParameterResolverFactory` file to the OSGi bundle containing the classes to resolve parameters for (i.e. the event handler).

It is not unlikely that most command handler operations have an identical structure: they load an Aggregate from a repository and call a method on the returned aggregate using values from the command as parameter. If that is the case, you might benefit from a generic command handler: the `AggregateAnnotationCommandHandler`. This command handler uses `@CommandHandler` annotations on the aggregate's methods to identify which methods need to be invoked for an incoming command. If the `@CommandHandler` annotation is placed on a constructor, that command will cause a new Aggregate instance to be created.

The `AggregateAnnotationCommandHandler` still needs to know which aggregate instance (identified by its unique Aggregate Identifier) to load and which version to expect. By default, the `AggregateAnnotationCommandHandler` uses annotations on the command object to find this information. The `@TargetAggregateIdentifier` annotation must be put on a field or getter method to indicate where the identifier of the target Aggregate can be found. Similarly, the `@TargetAggregateVersion` may be used to indicate the expected version.

The `@TargetAggregateIdentifier` annotation can be placed on a field or a method. The latter case will use the return value of a method invocation (without parameters) as the value to use.

If you prefer not to use annotations, the behavior can be overridden by supplying a custom `CommandTargetResolver`. This class should return the Aggregate Identifier and expected version (if any) based on a given command.

> **Note**
>
> When the `@CommandHandler` annotation is placed on an Aggregate's constructor, the respective command will create a new instance of that aggregate and add it to the repository. Those commands do not require to target a specific aggregate instance. That wouldn't make sense, since the instance is yet to be created. Therefore, those commands do not require any `@TargetAggregateIdentifier` or `@TargetAggregateVersion` annotations, nor will a custom `CommandTargetResolver` be invoked for these commands.
>
> When a command creates an aggregate instance, the callback for that command will receive the aggregate identifier when the command executed successfully.

``` java
public class MyAggregate extends AbstractAnnotatedAggregateRoot {

    @AggregateIdentifier
    private String id;

    @CommandHandler
    public MyAggregate(CreateMyAggregateCommand command) {
        apply(new MyAggregateCreatedEvent(IdentifierFactory.getInstance().generateIdentifier()));
    }

    // no-arg constructor for Axon
    MyAggregate() {
    }

    @CommandHandler
    public void doSomething(DoSomethingCommand command) {
        // do something...
    }

    // code omitted for brevity. The event handler for MyAggregateCreatedEvent must set the id field
}

public class DoSomethingCommand {

    @TargetAggregateIdentifier
    private String aggregateId;

    // code omitted for brevity

}

// to generate the command handlers for this aggregate:
AggregateAnnotationCommandHandler handler = AggregateAnnotationCommandHandler.subscribe(MyAggregate.class, repository);
// or when using another type of CommandTargetResolver:
AggregateAnnotationCommandHandler handler = AggregateAnnotationCommandHandler.subscribe(MyAggregate.class, repository, myOwnCommandTargetResolver);

// to subscribe:
for (String supportedCommand : handler.supportedCommands()) {
    commandBus.subscribe(supportedCommand, handler);
}

// to unsubscribe:
for (String supportedCommand : handler.supportedCommands()) {
    commandBus.unsubscribe(supportedCommand, handler);
}
```

> **Note**
>
> If you use Spring, you can use the `<axon:aggregate-command-handler/>` element in your application context to create a Command Handler that delegates commands to an aggregate instance. It will also be automatically subscribed to the `CommandBus`.

Since version 2.2, `@CommandHandler` annotations are not limited to the aggregate root. Placing all command handlers in the root will sometimes lead to a large number of methods on the aggregate root, while many of them simply forward the invocation to one of the underlying entities. If that is the case, you may place the `@CommandHandler` annotation on one of the underlying entities' methods. For Axon to find these annotated methods, the field declaring the entity in the aggregate root must be marked with `@CommandHandlingMember`. Note that only the declared type of the annotated field is inspected for Command Handlers. If a field value is null at the time an incoming command arrives for that entity, an exception is thrown.

    public class MyAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        @CommandHandlingMember
        private MyEntity entity;

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command) {
            apply(new MyAggregateCreatedEvent(IdentifierFactory.getInstance().generateIdentifier()));
        }

        // no-arg constructor for Axon
        MyAggregate() {
        }

        @CommandHandler
        public void doSomething(DoSomethingCommand command) {
            // do something...
        }

        // code omitted for brevity. The event handler for MyAggregateCreatedEvent must set the id field
        // and somewhere in the lifecycle, a value for "entity" must be assigned to be able to accept
        // DoSomethingInEntityCommand commands.
    }

    public class MyEntity extends AbstractAnnotatedEntity { // extends not strictly required for @CommandHandler

        @CommandHandler
        public void handleSomeCommand(DoSomethingInEntityCommand command) {
            // do something
        }
    }

> **Note**
>
> Note that each command must have exactly one handler in the aggregate. This means that you cannot annotate multiple entities (either root nor not) with @CommandHandler, when they handle the same command type. This also means that it is not possible to annotate fields containing collections with `@CommandHandlingMember`. In case you need to conditionally route a command to an entity, the parent of these entities should handle the command, and forward it based on the conditions that apply.
>
> The runtime type of the field does not have to be exactly the declared type. However, only the declared type of the `@CommandHandlingMember` annotated field is inspected for `@CommandHandler` methods.

Since version 2.4, it is also possible to annotate Collections of entities with `@CommandHandlingMemberCollection` and Maps with `@CommandHandlingMemberMap`. In the latter case, the values of the map must contain the entities, while the key contains a value that is used as their reference.

The `@CommandHandlingMemberCollection` annotation takes two mandatory properties: `entityId` and `commandTargetProperty`. Both are references to accessor methods that provide the values necessary for Axon to figure out which entity to forward the command to. The `entityId` property is used to identify the property on the aggregate, while `commandTargetProperty` identifies the property on the incoming command. If the returned values are `equals()` for a command/entity combination, the command is forwarded to that specific entity. On its turn, the entity may have other annotated fields to forward the command further.

The `@CommandHandlingMemberMap` is similar to the previous annotation, except that it only has the `commandTargetProperty` property. It identifies the property on the command that returns the value to use as a key for the Map. The entity returned from the map by that key receives the command.

If no Entity can be found in the annotated Collection or Map, Axon throws an IllegalStateException; apparently, the aggregate is not capable of processing that command at that point in time.

> **Note**
>
> The field declaration for both the Collection or Map should contain proper generics to allow Axon to identify the type of Entity contained in the Collection or Map. If it is not possible to add the generics in the declaration (e.g. because you're using a custom implementation which already defines generic types), you must specify the type of entity used in the `entityType` property on the annotation.

Unit of Work
============

The Unit of Work is an important concept in the Axon Framework. The processing of a command can be seen as a single unit. Each time a command handler performs an action, it is tracked in the current Unit of Work. When command handling is finished, the Unit of Work is committed and all actions are finalized. This means that any repositories are notified of state changes in their aggregates and events scheduled for publication are sent to the Event Bus.

The Unit of Work serves two purposes. First, it makes the interface towards repositories a lot easier, since you do not have to explicitly save your changes. Secondly, it is an important hook-point for interceptors to find out what a command handler has done.

In most cases, you are unlikely to need access to the Unit of Work. It is mainly used by the building blocks that Axon provides. If you do need access to it, for whatever reason, there are a few ways to obtain it. The Command Handler receives the Unit Of Work through a parameter in the handle method. If you use annotation support, you may add a parameter of type `UnitOfWork` to your annotated method. In other locations, you can retrieve the Unit of Work bound to the current thread by calling `CurrentUnitOfWork.get()`. Note that this method will throw an exception if there is no Unit of Work bound to the current thread. Use `CurrentUnitOfWork.isStarted()` to find out if one is available.

One reason to require access to the current Unit of Work is to dispatch Events as part of a transaction, but those Events do not originate from an Aggregate. For example, you might want to publish an Event from a Command Handler, but don't require that Event to be stored as state in an Event Store. In such case, you can call `CurrentUnitOfWork.get().publishEvent(event, eventBus)`, where `event` is the Event (Message) to publish and `EventBus` the Bus to publish it on. The actual publication of the message is postponed unit the Unit of Work is committed, respecting the order in which Events have been registered.

> **Note**
>
> Note that the Unit of Work is merely a buffer of changes, not a replacement for Transactions. Although all staged changes are only committed when the Unit of Work is committed, its commit is not atomic. That means that when a commit fails, some changes might have been persisted, while other are not. Best practices dictate that a Command should never contain more than one action. If you stick to that practice, a Unit of Work will contain a single action, making it safe to use as-is. If you have more actions in your Unit of Work, then you could consider attaching a transaction to the Unit of Work's commit. See [simplesect\_title](#binding-uow-to-tx).

Your command handlers may throw an Exception as a result of command processing. By default, unchecked exceptions will cause the UnitOfWork to roll back all changes. As a result, no Events are stored or published. In some cases, however, you might want to commit the Unit of Work and still notify the dispatcher of the command of an exception through the callback. The `SimpleCommandBus` allows you to provide a `RollbackConfiguration`. The `RollbackConfiguration` instance indicates whether an exception should perform a rollback on the Unit of Work, or a commit. Axon provides two implementation, which should cover most of the cases.

The `RollbackOnAllExceptionsConfiguration` will cause a rollback on any exception (or error). The default configuration, the `RollbackOnUncheckedExceptionConfiguration`, will commit the Unit of Work on checked exceptions (those not extending `RuntimeException`) while still performing a rollback on Errors and Runtime Exceptions.

When using a Command Bus, the lifecycle of the Unit of Work will be automatically managed for you. If you choose not to use explicit command objects and a Command Bus, but a Service Layer instead, you will need to programmatically start and commit (or roll back) a Unit of Work instead.

In most cases, the DefaultUnitOfWork will provide you with the functionality you need. It expects Command processing to happen within a single thread. To start a new Unit Of Work, simply call `DefaultUnitOfWork.startAndGet();`. This will start a Unit of Work, bind it to the current thread (making it accessible via `CurrentUnitOfWork.get()`), and return it. When processing is done, either invoke `unitOfWork.commit();` or `unitOfWork.rollback(optionalException)`.

Typical usage is as follows:

``` java
UnitOfWork uow = DefaultUnitOfWork.startAndGet();
try {
    // business logic comes here
    uow.commit();
} catch (Exception e) {
    uow.rollback(e);
    // maybe rethrow...
}
```

A Unit of Work knows several phases. Each time it progresses to another phase, the UnitOfWork Listeners are notified.

-   Active phase: this is where the Unit of Work starts. Each time an event is registered with the Unit of Work, the `onEventRegistered` method is called. This method may alter the event message, for example to attach meta data to it. The return value of the method is the new EventMessage instance to use.

-   Commit phase: before a Unit of Work is committed, the listeners' `onPrepareCommit` methods are invoked. This method is provided with the set of aggregates and list of Event Messages being stored. If a Unit of Work is bound to a transaction, the `onPrepareTransactionCommit` method is invoked. When the commit succeeded, the `afterCommit` method is invoked. If a commit failed, the `onRollback` is used. This method has a parameter which defines the cause of the failure, if available.

-   Cleanup phase: This is the phase where any of the resources held by this Unit of Work (such as locks) are to be released. If multiple Units Of Work are nested, the cleanup phase is postponed until the outer unit of work is ready to clean up.

The command handling process can be considered an atomic procedure; it should either be processed entirely, or not at all. Axon Framework uses the Unit Of Work to track actions performed by the command handlers. After the command handler completed, Axon will try to commit the actions registered with the Unit Of Work. This involves storing modified aggregates (see [Domain Modeling](4-domain-modeling.md#domain-modeling)) in their respective repository (see [Repositories and Event Stores](5-repositories-and-event-stores.md)) and publishing events on the Event Bus (see [Event Processing](6-event-listeners.md#event-processing)).

The Unit Of Work, however, it is not a replacement for a transaction. The Unit Of Work only ensures that changes made to aggregates are stored upon successful execution of a command handler. If an error occurs while storing an aggregate, any aggregates already stored are not rolled back.

It is possible to bind a transaction to a Unit of Work. Many CommandBus implementations, like the SimpleCommandBus and DisruptorCommandBus, allow you to configure a Transaction Manager. This Transaction Manager will then be used to create the transactions to bind to the Unit of Work that is used to manage the process of a Command. When a Unit of Work is bound to a transaction, it will ensure the bound transaction is committed at the right point in time. It also allows you to perform actions just before the transaction is committed, through the `UnitOfWorkListener`'s `onPrepareTransactionCommit` method.

When creating Unit of Work programmatically, you can use the `DefaultUnitOfWork.startAndGet(TransactionManager)` method to create a Unit of Work that is bound to a transaction. Alternatively, you can initialize the `DefaultUnitOfWorkFactory` with a `TransactionManager` to allow it to create Transaction-bound Unit of Work.

When application components need resources at different stages of Command processing, such as a Database Connection or an EntityManager, these resources can be attached to the Unit of Work. By using the `attachResource(String name,
                    Object resource)` method, you attach a resource to the Unit of Work, which can be retrieved using the `getResource(String name)` method. You can also attach a resource that is also used by any nested Unit of Work using the `attachResource(String name, Object resource, boolean inherited)`, by providing inherited as `true`.

Command Interceptors
====================

One of the advantages of using a command bus is the ability to undertake action based on all incoming commands. Examples are logging or authentication, which you might want to do regardless of the type of command. This is done using Interceptors.

There are two types of interceptors: Command Dispatch Interceptors and Command Handler Interceptors. The former are invoked before a command is dispatched to a Command Handler. At that point, it may not even be sure that any handler exists for that command. The latter are invoked just before the Command Handler is invoked.

Command Dispatch Interceptors
-----------------------------

Command Dispatch Interceptors are invoked when a command is dispatched on a Command Bus. They have the ability to alter the Command Message, by adding Meta Data, for example, or block the command by throwing an Exception. These interceptors are always invoked on the thread that dispatches the Command.

### Structural validation

There is no point in processing a command if it does not contain all required information in the correct format. In fact, a command that lacks information should be blocked as early as possible, preferably even before any transaction is started. Therefore, an interceptor should check all incoming commands for the availability of such information. This is called structural validation.

Axon Framework has support for JSR 303 Bean Validation based validation. This allows you to annotate the fields on commands with annotations like `@NotEmpty` and `@Pattern`. You need to include a JSR 303 implementation (such as Hibernate-Validator) on your classpath. Then, configure a `BeanValidationInterceptor` on your Command Bus, and it will automatically find and configure your validator implementation. While it uses sensible defaults, you can fine-tune it to your specific needs.

> **Tip**
>
> You want to spend as few resources on an invalid command as possible. Therefore, this interceptor is generally placed in the very front of the interceptor chain. In some cases, a Logging or Auditing interceptor might need to be placed in front, with the validating interceptor immediately following it.

The BeanValidationInterceptor also implements `CommandHandlerInterceptor`, allowing you to configure it as a Handler Interceptor as well.

Command Handler Interceptors
----------------------------

Command Handler Interceptors can take action both before and after command processing. Interceptors can even block command processing altogether, for example for security reasons.

Interceptors must implement the `CommandHandlerInterceptor` interface. This interface declares one method, `handle`, that takes three parameters: the command message, the current `UnitOfWork` and an `InterceptorChain`. The `InterceptorChain` is used to continue the dispatching process.

### Auditing

Well designed events will give clear insight in what has happened, when and why. To use the event store as an Audit Trail, which provides insight in the exact history of changes in the system, this information might not be enough. In some cases, you might want to know which user caused the change, using what command, from which machine, etc.

The `AuditingInterceptor` is an interceptor that allows you to attach arbitrary information to events just before they are stored or published. The `AuditingInterceptor` uses an `AuditingDataProvider` to retrieve the information to attach to these events. You need to provide the implementation of the `AuditingDataProvider` yourself.

An Audit Logger may be configured to write to an audit log. To do so, you can implement the `AuditLogger` interface and configure it in the `AuditingInterceptor`. The audit logger is notified both on successful execution of the command, as well as when execution fails. If you use event sourcing, you should be aware that the event log already contains the exact details of each event. In that case, it could suffice to just log the event identifier or aggregate identifier and sequence number combination.

> **Note**
>
> Note that the log method is called in the same thread as the command processing. This means that logging to slow sources may result in higher response times for the client. When important, make sure logging is done asynchronously from the command handling thread.

Event Messages are immutable. The Unit of Work listeners allow you to alter information in an Event Message in the onEventRegistered() method. However, in some cases, you need to attach information to Events after all events have been registered with the Unit of Work. Axon provides the abstract `MetaDataMutatingUnitOfWorkListenerAdapter`, which allows you to assign meta-data to Events just before the Unit of Work is committed. Simply extend the MetaDataMutatingUnitOfWorkListenerAdapter class and implement the abstract `assignMetaData(EventMessage event, List<EventMessage> events,
                        int index)` method. The first parameter is the EventMessage to provide the Meta Data for. The `List<EventMessage>` and the `index` are respectively the complete list of events part of this commit and the index at which the first parameter can be found in the list.

Distributing the Command Bus
============================

The CommandBus implementations described in [Command Bus](3-command-handling.md#command-bus) only allow Command Messages to be dispatched within a single JVM. Sometimes, you want multiple instances of Command Buses in different JVM's to act as one. Commands dispatched on one JVM's Command Bus should be seamlessly transported to a Command Handler in another JVM while sending back any results.

That's where the `DistributedCommandBus` comes in. Unlike the other `CommandBus` implementations, the `DistributedCommandBus` does not invoke any handlers at all. All it does is form a "bridge" between Command Bus implementations on different JVM's. Each instance of the `DistributedCommandBus` on each JVM is called a "Segment".

<img src="distributed-command-bus.svg" alt="Structure of the Distributed Command Bus" width="377" />

> **Note**
>
> The distributed command bus is not part of the Axon Framework Core module, but in the *axon-distributed-commandbus* module. If you use Maven, make sure you have the appropriate dependencies set. The groupId and version are identical to those of the Core module.

The `DistributedCommandBus` relies on two components: a `CommandBusConnector`, which implements the communication protocol between the JVM's, and the `RoutingStrategy`, which provides a Routing Key for each incoming Command. This Routing Key defines which segment of the Distributed Command Bus should be given a Command. Two commands with the same routing key will always be routed to the same segment, as long as there is no change in the number and configuration of the segments. Generally, the identifier of the targeted aggregate is used as a routing key.

Two implementations of the RoutingStrategy are provided: the `MetaDataRoutingStrategy`, which uses a Meta Data property in the Command Message to find the routing key, and the `AnnotationRoutingStrategy`, which uses the `@TargetAggregateIdentifier` annotation on the Command Messages payload to extract the Routing Key. Obviously, you can also provide your own implementation.

By default, the RoutingStrategy implementations will throw an exception when no key can be resolved from a Command Message. This behavior can be altered by providing a UnresolvedRoutingKeyPolicy in the constructor of the MetaDataRoutingStrategy or AnnotationRoutingStrategy. There are three possible policies:

-   ERROR: This is the default, and will cause an exception to be thrown when a Routing Key is not available

-   RANDOM\_KEY: Will return a random value when a Routing Key cannot be resolved from the Command Message. This effectively means that those commands will be routed to a random segment of the Command Bus.

-   STATIC\_KEY: Will return a static key (being "unresolved") for unresolved Routing Keys. This effectively means that all those commands will be routed to the same segment, as long as the configuration of segments does not change.

JGroupsConnector
----------------

The `JGroupsConnector` uses (as the name already gives away) JGroups as the underlying discovery and dispatching mechanism. Describing the feature set of JGroups is a bit too much for this reference guide, so please refer to the [JGroups User Guide](http://www.jgroups.org/ug.html) for more details.

The JGroupsConnector has four mandatory configuration elements:

-   The first is a JChannel, which defines the JGroups protocol stack. Generally, a JChannel is constructed with a reference to a JGroups configuration file. JGroups comes with a number of default configurations which can be used as a basis for your own configuration. Do keep in mind that IP Multicast generally doesn't work in Cloud Services, like Amazon. TCP Gossip is generally a good start in such type of environment.

-   The Cluster Name defines the name of the Cluster that each segment should register to. Segments with the same Cluster Name will eventually detect each other and dispatch Command among each other.

-   A "local segment" is the Command Bus implementation that dispatches Commands destined for the local JVM. These commands may have been dispatched by instances on other JVM's or from the local one.

-   Finally, the Serializer is used to serialize command messages before they are sent over the wire.

Optionally, a `HashChangeListener` may be configured. This listener will be notified when the `ConsistentHash` used to route messages changes.

> **Note**
>
> When using a Cache, it should be cleared out when the `ConsistentHash` changes to avoid potential data corruption (e.g. when commands don't specify a `@TargetAggregateVersion` and an new member quickly joins and leaves the JGroup, modifying the aggregate while it's still cached elsewhere.)

Ultimately, the JGroupsConnector needs to actually connect, in order to dispatch Messages to other segments. To do so, call the `connect()` method. It takes a single parameter: the load factor. The load factor defines how much load, relative to the other segments this segment should receive. A segment with twice the load factor of another segment will be assigned (approximately) twice the amount of routing keys as the other segments. Note that when commands are unevenly distributed over the routing keys, segments with lower load factors could still receive more command than a segment with a higher load factor.

``` java
JChannel channel = new JChannel("path/to/channel/config.xml");
CommandBus localSegment = new SimpleCommandBus();
Serializer serializer = new XStreamSerializer();

JGroupsConnector connector = new JGroupsConnector(channel, "myCommandBus", localSegment, serializer);
DistributedCommandBus commandBus = new DistributedCommandBus(connector);

// on one node:
connector.connect(50);
commandBus.subscribe(CommandType.class.getName(), handler);

// on another node with more CPU:
connector.connect(150);
commandBus.subscribe(CommandType.class.getName(), handler);
commandBus.subscribe(AnotherCommandType.class.getName(), handler2);

// from now on, just deal with commandBus as if it is local...
```

> **Note**
>
> Note that it is not required that all segments have Command Handlers for the same type of Commands. You may use different segments for different Command Types altogether. The Distributed Command Bus will always choose a node to dispatch a Command to that has support for that specific type of Command.

If you use Spring, you may want to consider using the `JGroupsConnectorFactoryBean`. It automatically connects the Connector when the ApplicationContext is started, and does a proper disconnect when the `ApplicationContext` is shut down. Furthermore, it uses sensible defaults for a testing environment (but should not be considered production ready) and autowiring for the configuration.
