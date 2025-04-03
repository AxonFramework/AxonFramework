# Axon Framework 5 - Getting Started Sample Application
Axon version: 5.0.0-M1

## Event Store implementation

Now only in-memory implementation of an Event Store is supported, but you can play around with the Command handling API. 
The in-memory Event Store supports the [DCB (Dynamic Consistency Boundary) concept](https://www.youtube.com/watch?v=IgigmuHHchI).

# Domain: University

## Bounded Context: Faculty

We have students and courses students can subscribe to. 
Firstly, we must study (no pun intended) the business requirements. Here they are:

A student can enroll in the faculty. The faculty can decide to create a course with assigned capacity—the number of
students who can subscribe to a course. The capacity of the course must be maintained. After the course has been
created, its capacity can be changed. A student can subscribe to a course and unsubscribe.

Let’s do a short modeling session to identify events.

* `StudentEnrolledFaculty` - a fact that a student has enrolled faculty
* `CourseCreated` - a fact that a course has been created with assigned capacity
* `CourseCapacityChanged` - a fact that the capacity of the course has changed
* `StudentSubscribed` - a fact that the student has subscribed to the course
* `StudentUnsubscribed` - a fact that the student has unsubscribed from the course

> Note that the implementation of this interface does not have to be in the domain itself; it can be in the integration
> layer that would delegate calls to the domain. This way, the domain can remain clean of any framework code. However,
> we decided to implement it right in the domain layer for the simplicity of this sample.


## 🏛️ Screaming Architecture (Vertical Slices)

The project follows a Screaming Architecture pattern organized around vertical slices that mirror Event Modeling concepts.

The package structure screams the capabilities of the system by making explicit: commands available to users, events that capture what happened, queries for retrieving information, business rules, and system automations.
This architecture makes it immediately obvious what the system can do, what rules govern those actions, and how different parts of the system interact through events.

Each module is structured into three distinct types of slices (packages `write`, `read`, `automation`) and there are events (package `events`) between them, which are a system backbone - a contract between all other parts:
Thanks to Axon Framework 5 support for State (entity) per Command Handler those slices can be totally independent and can be developed in parallel (before they needed to share the Aggregate).

### Write Slices
Contains commands that represent user intentions, defines business rules through aggregates, produces domain events, and enforces invariants (e.g., SubscribeStudent command → StudentSubscribed event, with SubscriptionsPerStudentNotExceedMax rule).

### Read Slices [FOR THE FUTURE MILESTONES]
Implements queries and read models optimized for specific use cases, with projectors that transform events into queryable state (e.g., GetSubscriptionsByStudentId query → StudentSubscriptionsReadModel).

### Automation Slices [FOR THE FUTURE MILESTONES]
Processes events to trigger subsequent actions, implementing system policies and workflows that connect different modules (e.g., WhenStudentSubscribedThenSendEmailNotification).

## 🧪 Testing
Tests using the production app configuration, follows the approach:
- write slice: given(events) -> when(command) -> then(events)
- read slice: given(events) -> then(read model)
- automation: when(event, state?) -> then(command)

Tests are focused on observable behavior, so the domain model can be refactored without changes in tests.

### Example: write slice

![EventModeling_GWT_TestCase_CreatureRecruitment.png](docs/images/EventModeling_GWT_TestCase_CreatureRecruitment.png)

```java
    @BeforeEach
void beforeEach() {
    var application = new UniversityAxonApplication();
    fixture = AxonTestFixture.with(application.configurer());
}

@Test
void successfulSubscription() {
    var courseId = CourseId.random();
    var studentId = StudentId.random();

    fixture.given()
           .event(new StudentEnrolledFaculty(studentId.raw(), "Mateusz", "Nowak"))
           .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Be a PRO", 2))
           .when()
           .command(new SubscribeStudent(studentId, courseId))
           .then()
           .events(new StudentSubscribed(studentId.raw(), courseId.raw()));
}
```