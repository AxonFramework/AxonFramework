Testing
=======

One of the biggest benefits of CQRS, and especially that of event sourcing is that it is possible to express tests purely in terms of Events and Commands. Both being functional components, Events and Commands have clear meaning to the domain expert or business owner. Not only does this mean that tests expressed in terms of Events and Commands have a clear functional meaning, it also means that they hardly depend on any implementation choices.

The features described in this chapter require the `axon-test` module, which can be obtained by configuring a maven dependency (use `<artifactId>axon-test</artifactId>`) or from the full package download.

The fixtures described in this chapter work with any testing framework, such as JUnit and TestNG.

Command Component Testing
=========================

The command handling component is typically the component in any CQRS based architecture that contains the most complexity. Being more complex than the others, this also means that there are extra test related requirements for this component. Simply put: the more complex a component, the better it must be tested.

Although being more complex, the API of a command handling component is fairly easy. It has a command coming in, and events going out. In some cases, there might be a query as part of command execution. Other than that, commands and events are the only part of the API. This means that it is possible to completely define a test scenario in terms of events and commands. Typically, in the shape of:

-   given certain events in the past,

-   when executing this command,

-   expect these events to be published and/or stored.

Axon Framework provides a test fixture that allows you to do exactly that. This GivenWhenThenTestFixture allows you to configure a certain infrastructure, composed of the necessary command handler and repository, and express your scenario in terms of given-when-then events and commands.

The following example shows the usage of the given-when-then test fixture with JUnit 4:

``` java
public class MyCommandComponentTest {
    private FixtureConfiguration fixture;

    @Before
    public void setUp() {
        fixture = Fixtures.newGivenWhenThenFixture(MyAggregate.class);
        MyCommandHandler myCommandHandler = new MyCommandHandler( fixture.getRepository());
        fixture.registerAnnotatedCommandHandler(myCommandHandler);
    }

    @Test
    public void testFirstFixture() {
        fixture.given(new MyEvent(1))
               .when(new TestCommand())
               .expectVoidReturnType()
               .expectEvents(new MyEvent(2));
        /*
        These four lines define the actual scenario and its expected
        result. The first line defines the events that happened in the
        past. These events define the state of the aggregate under test.
        In practical terms, these are the events that the event store
        returns when an aggregate is loaded. The second line defines the
        command that we wish to execute against our system. Finally, we
        have two more methods that define expected behavior. In the
        example, we use the recommended void return type. The last method
        defines that we expect a single event as result of the command
        execution.
        /*
    }
}
```

 1. This line creates a fixture instance that can deal with given-when-then style tests. It is created in configuration stage, which allows us to configure the components that we need to process the command, such as the command handler and repository. An event bus and command bus are automatically created as part of the fixture.
 1. The `getRepository()` method returns an `EventSourcingRepository` instance capable of storing `MyAggregate` instances. This requires some conventions on the MyAggregate class, as described in [Event Sourcing Repositories](5-repositories-and-event-stores.md#event-sourcing-repositories). If there is need for a custom `AggregateFactory`, use the `registerRepository(...)` method to register another repository with the correct `AggregateFactory`.
 1. The `registerAnnotatedCommandHandler` method will register any bean as being an `@CommandHandler` with the command bus. All supported command types are automatically registered with the command bus.




The given-when-then test fixture defines three stages: configuration, execution and validation. Each of these stages is represented by a different interface: `FixtureConfiguration`, `TestExecutor` and `ResultValidator`, respectively. The static `newGivenWhenThenFixture()` method on the `Fixtures` class provides a reference to the first of these, which in turn may provide the validator, and so forth.

> **Note**
>
> To make optimal use of the migration between these stages, it is best to use the fluent interface provided by these methods, as shown in the example above.

During the configuration phase (i.e. before the first "given" is provided), you provide the building blocks required to execute the test. Specialized versions of the event bus, command bus and event store are provided as part of the fixture. There are getters in place to obtain references to them. The repository and command handlers need to be provided. This can be done using the `registerRepository` and `registerCommandHandler` (or `registerAnnotatedCommandHandler`) methods. If your aggregate allows the use of a generic repository, you can use the `createGenericRepository` method to create a generic repository and register it with the fixture in a single call. The example above uses this feature.

If the command handler and repository are configured, you can define the "given" events. The test fixture will wrap these events as DomainEventMessage. If the "given" event implements Message, the payload and meta data of that message will be included in the DomainEventMessage, otherwise the given event is used as payload. The sequence numbers of the DomainEventMessage are sequential, starting at 0.

Alternatively, you may also provide commands as "given" scenario. In that case, the events generated by those commands will be used to event source the Aggregate when executing the actual command under test. Use the "`givenCommands(...)`" method to provide Command objects.

The execution phase allows you to provide a command to be executed against the command handling component. That's all. Note that successful execution of this command requires that a command handler that can handle this type of command has been configured with the test fixture.

> **Note**
>
> During the execution of the test, Axon attempts to detect any illegal state changes in the Aggregate under test. It does so by comparing the state of the Aggregate after the command execution to the state of the Aggregate if it sourced from all "given" and stored events. If that state is not identical, this means that a state change has occurred outside of an Aggregate's Event Handler method. Static and transient fields are ignored in the comparison, as they typically contain references to resources.
>
> You can switch detection in the configuration of the fixture with the `setReportIllegalStateChange` method.

The last phase is the validation phase, and allows you to check on the activities of the command handling component. This is done purely in terms of return values and events (both stored and dispatched).

The test fixture allows you to validate return values of your command handlers. You can explicitly define the expected return value, which might be void or any arbitrary value. You may also express any exceptions you expect the CommandHandler to throw.

The other component is validation of stored and dispatched events. In most cases, the stored and dispatched events are equal. In some cases however, you may dispatch events (e.g. `ApplicationEvent`) that are not stored in the event store. In the first case, you can use the `expectEvents` method to validate events. In the latter case, you may use the `expectPublishedEvents` and `expectStoredEvents` methods to validate published and stored events, respectively.

There are two ways of matching expected events.

The first is to pass in Event instances that need to be literally compared with the actual events. All properties of the expected Events are compared (using `equals()`) with their counterparts in the actual Events. If one of the properties is not equal, the test fails and an extensive error report is generated.

The other way of expressing expectancies is using Matchers (provided by the Hamcrest library). `Matcher` is an interface prescribing two methods: `matches(Object)` and `describeTo(Description)`. The first returns a boolean to indicate whether the matcher matches or not. The second allows you to express your expectation. For example, a "GreaterThanTwoMatcher" could append "any event with value greater than two" to the description. Descriptions allow expressive error messages to be created about why a test case fails.

Creating matchers for a list of events can be tedious and error-prone work. To simplify things, Axon provides a set of matchers that allow you to provide a set of event specific matchers and tell Axon how they should match against the list.

Below is an overview of the available Event List matchers and their purpose:

-   **List with all of**: `Matchers.listWithAllOf(event matchers...)`

    This matcher will succeed if all of the provided Event Matchers match against at least one event in the list of actual events. It does not matter whether multiple matchers match against the same event, nor if an event in the list does not match against any of the matchers.

-   **List with any of**: `Matchers.listWithAnyOf(event matchers...)`

    This matcher will succeed if one of more of the provided Event Matchers matches against one or more of the events in the actual list of events. Some matchers may not even match at all, while another matches against multiple others.

-   **Sequence of Events**: `Matchers.sequenceOf(event matchers...)`

    Use this matcher to verify that the actual Events are match in the same order as the provided Event Matchers. It will succeed if each Matcher matches against an Event that comes after the Event that the previous matcher matched against. This means that "gaps" with unmatched events may appear.

    If, after evaluating the events, more matchers are available, they are all matched against "`null`". It is up to the Event Matchers to decide whether they accept that or not.

-   **Exact sequence of Events**: `Matchers.exactSequenceOf(event matchers...)`

    Variation of the "Sequence of Events" matcher where gaps of unmatched events are not allowed. This means each matcher must match against the Event directly following the Event the previous matcher matched against.

For convenience, a few commonly required Event Matchers are provided. They match against a single Event instance:

-   **Equal Event**: `Matchers.equalTo(instance...)`

    Verifies that the given object is semantically equal to the given event. This matcher will compare all values in the fields of both actual and expected objects using a null-safe equals method. This means that events can be compared, even if they don't implement the equals method. The objects stored in fields of the given parameter *are* compared using equals, requiring them to implement one correctly.

-   **No More Events**: `Matchers.andNoMore()` or `Matchers.nothing()`

    Only matches against a `null` value. This matcher can be added as last matcher to the Exact Sequence of Events matchers to ensure that no unmatched events remain.

Since the matchers are passed a list of Event Messages, you sometimes only want to verify the payload of the message. There are matchers to help you out:

-   **Payload Matching**: `Matchers.messageWithPayload(payload matcher)`

    Verifies that the payload of a Message matches the given payload matcher.

-   **Payloads Matching**: `Matchers.payloadsMatching(list matcher)`

    Verifies that the payloads of the Messages matches the given matcher. The given matcher must match against a list containing each of the Messages payload. The Payloads Matching matcher is typically used as the outer matcher to prevent repetition of payload matchers.

Below is a small code sample displaying the usage of these matchers. In this example, we expect two events to be stored and published. The first event must be a "ThirdEvent", and the second "aFourthEventWithSomeSpecialThings". There may be no third event, as that will fail against the "andNoMore" matcher.

``` java
fixture.given(new FirstEvent(), new SecondEvent())
       .when(new DoSomethingCommand("aggregateId"))
       .expectEventsMatching(exactSequenceOf(
           // we can match against the payload only:
           messageWithPayload(equalTo(new ThirdEvent())),
           // this will match against a Message
           aFourthEventWithSomeSpecialThings(),
           // this will ensure that there are no more events
           andNoMore()
       ));

// or if we prefer to match on payloads only:
       .expecteEventsMatching(payloadsMatching(
               exactSequenceOf(
                   // we only have payloads, so we can equalTo directly
                   equalTo(new ThirdEvent()),
                   // now, this matcher matches against the payload too
                   aFourthEventWithSomeSpecialThings(),
                   // this still requires that there is no more events
                   andNoMore()
               )
       ));
```

Testing Annotated Sagas
=======================

Similar to Command Handling components, Sagas have a clearly defined interface: they only respond to Events. On the other hand, Saga's have a notion of time and may interact with other components as part of their event handling process. Axon Framework's test support module contains fixtures that help you writing tests for sagas.

Each test fixture contains three phases, similar to those of the Command Handling component fixture described in the previous section.

-   given certain events (from certain aggregates),

-   when an event arrives or time elapses,

-   expect certain behavior or state.

Both the "given" and the "when" phases accept events as part of their interaction. During the "given" phase, all side effects, such as generated commands are ignored, when possible. During the "when" phase, on the other hand, events and commands generated from the Saga are recorded and can be verified.

The following code sample shows an example of how the fixtures can be used to test a saga that sends a notification if an invoice isn't paid within 30 days:AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(InvoicingSaga.class); fixture.givenAggregate(invoiceId).published(new InvoiceCreatedEvent()) .whenTimeElapses(Duration.ofDays(31)) .expectDispatchedCommandsMatching(Matchers.listWithAllOf(aMarkAsOverdueCommand())); // or, to match against the payload of a Command Message only .expectDispatchedCommandsMatching(Matchers.payloadsMatching( Matchers.listWithAllOf(aMarkAsOverdueCommand()))); Creates a fixture to test the InvoiceSaga class Notifies the saga that a specific aggregate (with id "invoiceId") has generated an event Tells the saga that time elapses, triggering events scheduled in that time frame Verifies that the saga has sent a command matching the return value of `aMarkAsOverdueCommand()` (a Hamcrest matcher)

Sagas can dispatch commands using a callback to be notified of Command processing results. Since there is no actual Command Handling done in tests, the behavior is defined using a `CallbackBehavior` object. This object is registered using `setCallbackBehavior()` on the fixture and defines if and how the callback must be invoked when a command is dispatched.

Instead of using a `CommandBus` directly, you can also use Command Gateways. See below on how to specify their behavior.

Often, Sagas will interact with external resources. These resources aren't part of the Saga's state, but are injected after a Saga is loaded or created. The test fixtures allow you to register resources that need to be injected in the Saga. To register a resource, simply invoke the `fixture.registerResource(Object)` method with the resource as parameter. The fixture will detect appropriate setter methods on the Saga and invoke it with an available resource.

> **Tip**
>
> It can be very useful to inject mock objects (e.g. Mockito or Easymock) into your Saga. It allows you to verify that the saga interacts correctly with your external resources.

Command Gateways provide Saga's with an easier way to dispatch Commands. Using a custom command gateway also makes it easier to create a mock or stub to define its behavior in tests. When providing a mock or stub, however, the actual command might not be dispatched, making it impossible to verify the sent commands in the test fixture.

Therefore, the fixture provides two methods that allow you to register Command Gateways and optionally a mock defining its behavior: `registerCommandGateway(Class)` and `registerCommandGateway(Class, Object)`. Both methods return an instance of the given class that represents the gateway to use. This instance is also registered as a resource, to make it eligible for resource injection.

When the `registerCommandGateway(Class)` is used to register a gateway, it dispatches Commands to the CommandBus managed by the fixture. The behavior of the gateway is mostly defined by the `CallbackBehavior` defined on the fixture. If no explicit `CallbackBehavior` is provided, callbacks are not invoked, making it impossible to provide any return value for the gateway.

When the `registerCommandGateway(Class, Object)` is used to register a gateway, the second parameter is used to define the behavior of the gateway.

The test fixture tries to eliminate elapsing system time where possible. This means that it will appear that no time elapses while the test executes, unless you explicitly state so using `whenTimeElapses()`. All events will have the timestamp of the moment the test fixture was created.

Having the time stopped during the test makes it easier to predict at what time events are scheduled for publication. If your test case verifies that an event is scheduled for publication in 30 seconds, it will remain 30 seconds, regardless of the time taken between actual scheduling and test execution.

> **Note**
>
> Time is stopped using Joda Time's `JodaTimeUtils` class. This means that the concept of stopped time is only visible when using Joda time's classes. The `System.currentTimeMillis()` will keep returning the actual date and time. Axon only uses Joda Time classes for Date and Time operations.

You can also use the `StubEventScheduler` independently of the test fixtures if you need to test scheduling of events. This `EventScheduler` implementation allows you to verify which events are scheduled for which time and gives you options to manipulate the progress of time. You can either advance time with a specific `Duration`, move the clock to a specific `DateTime` or advance time to the next scheduled event. All these operations will return the events scheduled within the progressed interval.
