Using Spring
============

The AxonFramework has many integration points with the Spring Framework. All major building blocks in Axon are Spring configurable. Furthermore, there are some Bean Post Processors that scan the application context for building blocks and automatically wire them.

In addition, the Axon Framework makes use of Spring's Extensible Schema-based configuration feature to make Axon application configuration even easier. Axon Framework has a Spring context configuration namespace of its own that allows you to create common configurations using Spring's XML configuration syntax, but in a more functionally expressive way than by wiring together explicit bean declarations.

Adding support for the Java Platform Common Annotations
=======================================================

Axon uses JSR 250 annotations (`@PostConstruct` and `@PreDestroy`) to annotate lifecycle methods of some of the building blocks. Spring doesn't always automatically evaluate these annotations. To force Spring to do so, add the `<context:annotation-config/>` tag to your application context, as shown in the example below:

``` xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:context="http://www.springframework.org/schema/context">

    <context:annotation-config/>

</beans>
```

Using the Axon namespace shortcut
=================================

As mentioned earlier, the Axon Framework provides a separate namespace full of elements that allow you to configure your Axon applications quickly when using Spring. In order to use this namespace you must first add the declaration for this namespace to your Spring XML configuration files.

Assume you already have an XML configuration file like this:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    ...
               
</beans>
```

To modify this configuration file to use elements from the Axon namespace, just add the following declarations:

``` xml
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:axon="http://www.axonframework.org/schema/core"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.axonframework.org/schema/core http://www.axonframework.org/schema/axon-core.xsd">
```

-   The declaration of the `axon` namespace reference that you will use through the configuration file.

-   Maps the Axon namespace to the XSD where the namespace is defined.

Wiring event and command handlers
=================================

Event handlers
--------------

Using the annotated event listeners is very easy when you use Spring. All you need to do is configure the `AnnotationEventListenerBeanPostProcessor` in your application context. This post processor will discover beans with `@EventHandler` annotated methods and automatically connect them to the event bus.&lt;beans xmlns="http://www.springframework.org/schema/beans"&gt; &lt;bean class="org...AnnotationEventListenerBeanPostProcessor"&gt; &lt;property name="eventBus" ref="eventBus"/&gt; &lt;/bean&gt; &lt;bean class="org.axonframework.sample.app.query.AddressTableUpdater"/&gt; &lt;/beans&gt; This bean post processor will scan the application context for beans with an `@EventHandler` annotated method. The reference to the event bus is optional, if only a single `EventBus` implementation is configured in the application context. The bean postprocessor will automatically find and wire it. If there is more than one `EventBus` in the context, you must specify the one to use in the postprocessor. This event listener will be automatically recognized and subscribed to the event bus.

You can also wire event listeners "manually", by explicitly defining them within a `AnnotationEventListenerAdapter` bean, as shown in the code sample below.&lt;beans xmlns="http://www.springframework.org/schema/beans"&gt; &lt;bean class="org.axonframework...annotation.AnnotationEventListenerAdapter"&gt; &lt;constructor-arg&gt; &lt;bean class="org.axonframework.sample.app.query.AddressTableUpdater"/&gt; &lt;/constructor-arg&gt; &lt;property name="eventBus" ref="eventBus"/&gt; &lt;/bean&gt; &lt;/beans&gt; The adapter turns any bean with `@EventHandler` methods into an `EventListener` You need to explicitly reference the event bus to which you like to register the event listener

> **Warning**
>
> Be careful when wiring event listeners "manually" while there is also an `AnnotationEventListenerBeanPostProcessor` in the application context. This will cause the event listener to be wired twice.

Command handlers
----------------

Wiring command handlers is very much like wiring event handlers: there is an `AnnotationCommandHandlerBeanPostProcessor` which will automatically register classes containing command handler methods (i.e. methods annotated with the `@CommandHandler` annotation) with a command bus. &lt;beans xmlns="http://www.springframework.org/schema/beans"&gt; &lt;bean class="org...AnnotationCommandHandlerBeanPostProcessor"&gt; &lt;property name="commandBus" ref="commandBus"/&gt; &lt;/bean&gt; &lt;bean class="org.axonframework.sample.app.command.ContactCommandHandler"/&gt; &lt;/beans&gt; This bean post processor will scan the application context for beans with a `@CommandHandler` annotated method. The reference to the command bus is optional, if only a single `CommandBus` implementation is configured in the application context. The bean postprocessor will automatically find and wire it. If there is more than one `CommandBus` in the context, you must specify the one to use in the postprocessor. This command handler will be automatically recognized and subscribed to the command bus.

As with event listeners, you can also wire command handlers "manually" by explicitly defining them within a `AnnotationCommandHandlerAdapter` bean, as shown in the code sample below.&lt;beans xmlns="http://www.springframework.org/schema/beans"&gt; &lt;bean class="org.axonframework...annotation.AnnotationCommandHandlerAdapter"&gt; &lt;constructor-arg&gt; &lt;bean class="org.axonframework.sample.app.command.ContactCommandHandler"/&gt; &lt;/constructor-arg&gt; &lt;property name="commandBus" ref="commandBus"/&gt; &lt;/bean&gt; &lt;/beans&gt; The adapter turns any bean with `@EventHandler` methods into an `EventListener` You need to explicitly reference the event bus to which you like to register the event listener

> **Warning**
>
> Be careful when wiring command handlers "manually" while there is also an `AnnotationCommandHandlerBeanPostProcessor` in the application context. This will cause the command handler to be wired twice.

When the `@CommandHandler` annotations are placed on the Aggregate, Spring will not be able to automatically configure them. You will need to specify a bean for each of the annotated Aggregate Roots as follows:

``` xml
<bean class="org.axonframework.commandhandling.annotation.AggregateAnnotationCommandHandler"
      init-method="subscribe">
    <constructor-arg value="fully.qualified.AggregateClass"/>
    <constructor-arg ref="ref-to-repo"/>
    <constructor-arg ref="ref-to-command-bus"/>
</bean>

<!-- or, when using Namespace support -->

<axon:aggregate-command-handler aggregate-type="fully.qualified.AggregateClass"
                                repository="ref-to-repo" 
                                command-bus="ref-to-command-bus"/>
```

Annotation support using the axon namespace
-------------------------------------------

The previous two sections explained how you wire bean post processors to activate annotation support for your command handlers and event listeners. Using support from the Axon namespace you can accomplish the same in one go, using the annotation-config element:

``` xml
<axon:annotation-config />
```

The annotation-config element has the following attributes that allow you to configure annotation support further:

| Attribute name | Usage       | Expected value type            | Description                                                                |
|----------------|-------------|--------------------------------|----------------------------------------------------------------------------|
| commandBus     | Conditional | Reference to a CommandBus Bean | Needed only if the application context contains more than one command bus. |
| eventBus       | Conditional | Reference to an EventBus Bean  | Needed only if the application context contains more than one event bus.   |

If you use Spring JavaConfig to configure Axon, you can place the `@AnnotationDriven` annotation on your config files. Similar to the namespace configuration described above, you can specify the bean names of the `EventBus` and `CommandBus` to register the components to. This is only required when there is more than one `EventBus` or `CommandBus` in the application context.

Wiring the event bus
====================

In a typical Axon application there is only one event bus. Wiring it is just a matter of creating a bean of a subtype of `EventBus`. The `SimpleEventBus` is the provided implementation.

``` xml
<beans xmlns="http://www.springframework.org/schema/beans">

    <bean id="eventBus" class="org.axonframework.eventhandling.SimpleEventBus"/>

</beans>

<!-- or using the namespace: -->

<axon:event-bus id="eventBus"/>
```

Configuration of Event Processors
---------------------------------

Using a Clustering Event Bus in Spring is very simple. In the Spring context, you just need to define the event processors you wish to use and tell them which Event Listeners you would like to be part of that Event Processor. Axon will create the necessary infrastructure to assign listeners to the event processors.

``` xml
<axon:event-processor id="myFirstEventProcessor">
    <axon:selectors>
        <axon:package prefix="com.mycompany.mypackage"/>
    </axon:selectors>
</axon:event-processor>

<axon:event-processor id="defaultEventProcessor" default="true"/>
```

The example above will create two event processors. Event Listeners in the package `com.mycompany.mypackage` will be assigned to `myFirstEventProcessor`, while all others are assigned to `defaultEventProcessor`. Note that the latter does not have any selectors. Selectors are optional if the event processor is a default.

If there are conflicting selectors, and you would like to influence the order in which they are evaluated, you can add the `order` attribute to an event processor. Event processors with a lower value are evaluated before those with a higher value. Only if there are no matching selectors at all, Axon will assign a Listener to the Event Processor with `default="true"`. If no suitable event processor for any listener is found, Axon throws an exception.

> **Tip**
>
> When you have an application that consists of a number of modules (represented in separate config files), it is possible to define the `<axon:event-processor>` in the Context where the listeners are also defined. This makes the application more modular and less dependent on a centralized configuration.

Replayable Event Processors
---------------------------

To make an event processor replayable, simply add the `<axon:replay-config>` element to a `<axon:event-processor>`, as in the example below:

``` xml
<axon:event-processor id="replayingEventProcessor">
    <axon:replay-config event-store="eventStore" transaction-manager="mockTransactionManager"/>
    <axon:selectors>
        <axon:package prefix="com.mycompany.mypackage"/>
    </axon:selectors>
</axon:event-processor>
```

The `<axon:replay-config>` element provides the necessary configuration to execute replays on an Event Processor. The resulting Event Processor bean will be of type `ReplayingEventProcessor`.

Custom Event Processor implementation
-------------------------------------

It is also possible to use the `<event-processor>` element while using a custom Event Processor implementation:

``` xml
<axon:event-processor id="customEventProcessor">
    <bean class="com.mycompany.MyPersonalEventProcessorImplementation"/>
    <axon:selectors>
        <axon:package prefix="com.mycompany.mypackage"/>
    </axon:selectors>
</axon:event-processor>
```

Wiring the command bus
======================

The command bus doesn't take any configuration to use. However, it allows you to configure a number of interceptors that should take action based on each incoming command.

``` xml
<beans xmlns="http://www.springframework.org/schema/beans">

    <bean id="commandBus" class="org.axonframework.commandhandling.SimpleCommandBus">
        <property name="handlerInterceptors">
            <list>
                <bean class="my-interceptors"/>
            </list>
        </property>
    </bean>

</beans>
```

Setting up a basic command bus using the Axon namespace is a piece of cake: you can use the `commandBus` element:

``` xml
<axon:command-bus id="commandBus"/>
```

Configuring command interceptors for your command bus is also possible using the `<axon:command-bus>` element, like so:

``` xml
<axon:command-bus id="commandBus">
    <axon:dispatchInterceptors>
        <bean class="..."/>
    </axon:dispatchInterceptors>
    <axon:handlerInterceptors>
        <bean class="..."/>
        <bean class="..."/>
    </axon:handlerInterceptors>
</axon:command-bus>
```

Of course you are not limited to bean references; you can also include local bean definitions if you want.

Wiring the Repository
=====================

Wiring a repository is very similar to any other bean you would use in a Spring application. Axon only provides abstract implementations for repositories, which means you need to extend one of them. See [Repositories and Event Stores](5-repositories-and-event-stores.md) for the available implementations.

Repository implementations that do support event sourcing just need the event bus to be configured, as well as any dependencies that your own implementation has.

``` xml
<bean id="simpleRepository" class="my.package.SimpleRepository">
    <property name="eventBus" ref="eventBus"/>
</bean>
```

Repositories that support event sourcing will also need an event store, which takes care of the actual storage and retrieval of events. The example below shows a repository configuration of a repository that extends the `EventSourcingRepository`.

``` xml
<bean id="contactRepository" class="org.axonframework.sample.app.command.ContactRepository">
    <property name="eventBus" ref="eventBus"/>
    <property name="eventStore" ref="eventStore"/>
</bean>
```

In many cases, you can use the `EventSourcingRepository`. Below is an example of XML application context configuration to wire such a repository.

``` xml
<bean id="myRepository" class="org.axonframework.eventsourcing.EventSourcingRepository">
    <constructor-arg value="fully.qualified.class.Name"/>
    <property name="eventBus" ref="eventBus"/>
    <property name="eventStore" ref="eventStore"/>
</bean>

<!-- or, when using the axon namespace -->

<axon:event-sourcing-repository id="myRepository"
                                aggregate-type="fully.qualified.class.Name"
                                event-bus="eventBus" event-store="eventStore"/>
```

The repository will delegate the storage of events to the configured `eventStore`, while these events are dispatched using the provided `eventBus`.

Wiring the Event Store
======================

All event sourcing repositories need an Event Store. Wiring the `JpaEventStore` and the `FileSystemEventStore` is very similar, but the `JpaEventStore` needs a way to get a hold of an EntityManager. In general, applications use a Container Managed EntityManager:

``` xml
<bean id="eventStore" class="org.axonframework.eventsourcing.eventstore.jpa.JpaEventStore">
    <constructor-arg>
        <bean class="org.axonframework.common.jpa.ContainerManagedEntityManagerProvider"/>
    </constructor-arg>
</bean>

<!-- declare transaction manager, data source, EntityManagerFactoryBean, etc -->
```

Using the Axon namespace support, you can quickly configure event stores backed either by the file system or a JPA layer using the one of the following elements:

``` xml
<axon:jpa-event-store id="jpaEventStore"/>

<axon:filesystem-event-store id="fileSystemEventStore" base-dir="/data"/>
```

The annotation support will automatically configure a Container Managed EntityManager on the Jpa Event Store, but you may also configure a custom implementation using the `entity-manager-provider` attribute. This is useful when an application uses more than one EntityManagerFactory.

Configuring Snapshotting
========================

Configuring snapshotting using Spring is not complex, but does require a number of beans to be configured in your application context.

The `EventCountSnapshotterTrigger` needs to be configured as a proxy for your event store. That means all repositories should load and save aggregate from the `EventCountSnapshotterTrigger`, instead of the actual event store.

``` xml
<bean id="myRepository" class="org.axonframework...GenericEventSourcingRepository">
    <!-- properties omitted for brevity -->
    <property name="snapshotterTrigger">
        <bean class="org.axonframework.eventsourcing.EventCountSnapshotterTrigger">
            <property name="trigger" value="20" />
        </bean>
    </property>
</bean>

<!-- or, when using the namespace -->

<axon:event-sourcing-repository> <!-- attributes omitted for brevity -->
    <axon:snapshotter-trigger event-count-threshold="20" snapshotter-ref="snapshotter"/>
</axon:event-sourcing-repository>
```

The sample above configures an EventCountSnapshotter trigger that will trigger Snapshot creation when 20 or more events are required to reload the aggregate's current state.

The snapshotter is configured as follows:

``` xml
<bean id="snapshotter" class="org.axonframework.spring.eventsourcing.SpringAggregateSnapshotter">
    <property name="eventStore" ref="eventStore"/>
    <property name="executor" ref="taskExecutor"/>
</bean>

<!-- or, when using the namespace -->

<axon:snapshotter id="snapshotter" event-store="eventStore" executor="taskExecutor"/>

<!-- the task executor attribute is optional. When used you can define (for example) a thread pool to perform the snapshotting -->
<bean id="taskExecutor" class="org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor">
    <property name="corePoolSize" value="2"/>
    <property name="maxPoolSize" value="5"/>
    <property name="waitForTasksToCompleteOnShutdown" value="true"/>
</bean>
```

The `SpringAggregateSnapshotter` will automatically detect any `PlatformTransactionManager` in your application context, as well as `AggregateFactory` instances, which all repositories typically are. That means you only need very little configuration to use a `Snapshotter` within Spring. If you have multiple `PlatformTransactionManager` beans in your context, you should explicitly configure the one to use.

Configuring Sagas
=================

To use Sagas, two infrastructure components are required: the SagaManager and the SagaRepository. Each have their own element in the Spring application context.

The SagaManager is defined as follows:

``` xml
<axon:saga-manager id="sagaManager" saga-repository="sagaRepository"
                   saga-factory="sagaFactory"
                   resource-injector="resourceInjector">
    <axon:async executor="taskExecutor" transaction-manager="transactionManager" />
    <axon:types>
        fully.qualified.ClassName
        another.ClassName
    </axon:types>
</axon:saga-manager>
```

All properties are optional. The `saga-repository` will default to an in-memory repository, meaning that Sagas will be lost when the VM is shut down. The `saga-factory` can be provided if the Saga instances do not have a no-argument accessible constructor, or when special initialization is required. An `async` element with `executor` can be provided if Sagas should not be invoked by the event dispatching thread. When using asynchronous event handling it is required to provide the `transaction-manager` attribute. The default resource injector uses the Spring Context to autowire Saga instances with Spring Beans.

Use the `types` element to provide a comma and/or newline separated list of fully qualified class names of the annotated sagas.

When an in-memory Saga repository does not suffice, you can easily configure one that uses JPA as persistence mechanism as follows:

``` xml
<axon:jpa-saga-repository id="sagaRepository" resource-injector="resourceInjector"
                          use-explicit-flush="true" saga-serializer="sagaSerializer"/>
```

The resource-injector, as with the saga manager, is optional and defaults to Spring-based autowiring. The saga-serializer defines how Saga instances need to be serialized when persisted. This defaults to an XStream based serialization mechanism. You may choose to explicitly flush any changes made in the repository immediately or postpone it until the transaction in which the changes were made are executed by setting the `use-explicit-flush` attribute to `true` or `false`, respectively. This property defaults to `true`.
