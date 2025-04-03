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