# Axon Framework 5 - Getting Started Sample Application
Axon version: `5.0.0-preview`

## Getting Started Guide
[Getting Started with Axon Framework 5](https://docs.axoniq.io/axon-framework-5-getting-started/) - there you will find a step-by-step introduction to the implementation showing how to move from Event Modeling to Vertical Slice Architecture (based on Dynamic Consistency Boundary) implementation.

If you want to clone this repository to work from, feel free to use the `base` branch to start fresh.

## Event Store implementation

### InMemory

The in-memory Event Store supports the [DCB (Dynamic Consistency Boundary) concept](https://www.youtube.com/watch?v=IgigmuHHchI), but it is not persistent, so it is not suitable for production use.
It's good for testing and development purposes.

### Axon Server (DCB support)

#### Docker container

To run the app with Axon Server, you need to have an instance of Axon Server running. You can run it using Docker:

```bash
docker compose up
```

#### Axon Server Context

Then you need to open the Axon Server UI at [http://localhost:8024](http://localhost:8024) and create a new context named `university`.
What is essential, you need also check the `DCB context (beta)` checkbox in the `General` settings tab.
![AxonServer_DCBContext_Creation.png](docs/images/AxonServer_DCBContext_Creation.png)

If you did not create the context, the command execution will fail with the following error:
```
org.axonframework.commandhandling.CommandExecutionException: Exception while handling command
Caused by: java.util.concurrent.ExecutionException: io.grpc.StatusRuntimeException: UNAVAILABLE
```

#### The app configuration

The application is configured to use Axon Server as the Event Store by default. 
If you want to use the in-memory Event Store, you can change the configuration in the `application.properties`.

# Domain: The Axon University

## Bounded Context: Faculty

In the faculty context there are students and courses, that students can subscribe to.

A student can enroll in the faculty. The administrators of the faculty can create a course with assigned capacity—the number of
students who can subscribe to a course. The capacity of the course must be maintained. After the course has been
created, its capacity can be changed. A student can subscribe to a course and unsubscribe.

![FacultyContext_EventModeling.png](docs/images/FacultyContext_EventModeling.png)

After an Event Modeling (which you can see above) session, we have identified the following events:
* `StudentEnrolledInFaculty` - A fact that a student has enrolled faculty.
* `CourseCreated` - A fact that a course has been created with assigned capacity.
* `CourseCapacityChanged` - A fact that the capacity of the course has changed.
* `StudentSubscribedToCourse` - A fact that the student has subscribed to the course.
* `StudentUnsubscribedFromCourse` - A fact that the student has unsubscribed from the course.


## 🏛️ Screaming Architecture (Vertical Slices)

The project follows a [Screaming Architecture](https://www.milanjovanovic.tech/blog/screaming-architecture) pattern organized around vertical slices that mirror Event Modeling concepts.

The package structure screams the capabilities of the system by making explicit: commands available to users, events that capture what happened, queries for retrieving information, business rules, and system automations.
This architecture makes it immediately obvious what the system can do, what rules govern those actions, and how different parts of the system interact through events.

Each module is structured into three distinct types of slices (packages `write`, `read`, `automation`) and there are events (package `events`) between them, which are a system backbone - a contract between all other parts:
Thanks to Axon Framework 5 support dynamic boundaries, each slice can have its own entities based on the same events, and thus be totally independent. There's no need for cross-slice communication and can be developed in parallel. In Axon Framework 4 and other Event-Sourcing frameworks, they needed to share an aggregate.
Any slice can be implemented differently. Thanks to the architecture of Axon Framework, what really matters are messages (Commands, Events and Queries) that shape the API.

### Write Slices
Contains commands that represent user intentions, define business rules through aggregates, produce domain events, and enforce invariants (e.g., SubscribeStudent command → StudentSubscribed event, with SubscriptionsPerStudentNotExceedMax rule).

### Read Slices [FOR THE FUTURE MILESTONES]
Implement queries and read models optimized for specific use cases, with projectors that transform events into queryable state (e.g., GetSubscriptionsByStudentId query → StudentSubscriptionsReadModel).

### Automation Slices [FOR THE FUTURE MILESTONES]
Processes events to trigger subsequent actions, implementing system policies and workflows that connect different modules (e.g., WhenStudentSubscribedThenSendEmailNotification).

## 🧪 Testing
Tests using the production app configuration, follows the approach:
- write slice: given(events) -> when(command) -> then(events)
- read slice: given(events) -> then(read model)
- automation: when(event, state?) -> then(command)

Tests are focused on observable behavior, so the domain model can be refactored without changes in tests.

### Example: write slice

![WriteSlice_GWT.png](docs/images/EventModeling_GWT_SubscribeStudent.png)

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
           .event(new CourseCreated(courseId.raw(), "Axon Framework 5: Getting Started", 2))
           .event(new StudentEnrolledInFaculty(studentId, "Mateusz", "Nowak"))
           .when()
           .command(new SubscribeStudentToCourse(studentId, courseId))
           .then()
           .events(new StudentSubscribedToCourse(studentId, courseId));
}
```
