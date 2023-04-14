# What does it mean to be reactive?

## Reactive systems
1. Means -> Message-driven
2. Form  -> Elastic
   Can we scale up or down resources within a single application.
   This is what allows backpressure, for example.
3. Form  -> Resilient
   Components deal with their own failures...(todo enhance)
4. Value -> Responsive

## Reactive programming
The only common denominator is:
- Asynchronous stream of data going through, open which you can attach operators

## Personas playing with our API

1. Procedural-minded people (Procedural Peter) -> don't like Publishers!
2. Whatever-minded people -> sure, make it async, I don't care.
3. Full-reactive-minded people (Async Angela) -> Publishers all the way!

The API we define should follow the implementation guidelines, helping all three.

# Concrete(ish) idea to provide both

## One-interface-approach

Not sticking to threads. So, no `ThreadLocals`!
But, we need a replacement to maintain the "context" wherein a `UnitOfWork` operates.

If we can define an interface that allows us to selectively pick either:
- `ThreadLocals`, or
- Project Reactor "reactive context" (todo lookup actual name)
  we should be able to serve both reactive and non-reactive scenarios

However, the API should be such that it allows both.
Since, ideally, we *do not* have different interfaces for reactive and non-reactive (e.g. Spring).

## Two-interfaces-approach

We can go the route that Spring has taken as well, thus providing two interfaces:

1. One using the `Publisher` API, and
2. another using a non-reactive approach.

The ease our development, we could still have both use the same underlying concrete implementation, though.

# Project Reactor - yes/no

## Pros

- Automatically reactive / "reactive-native"
- API

## Cons

- Losing stack traces (ease of debugability)
- Transaction management!

# Useful links (perhaps)

- Our end flow-control library - https://github.com/AxonIQ/flow-control/blob/master/grpc-manual-flow-control/src/main/java/io/axoniq/flowcontrol/producer/grpc/FlowControlledOutgoingStream.java
- Spring's adapter object to switch between reactive formats - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/ReactiveAdapter.html
- Reactive Manifesto - https://www.reactivemanifesto.org/
- Reactive Programming by Baeldung - https://www.baeldung.com/cs/reactive-programming
- RxJava JDBC implementation - https://github.com/davidmoten/rxjava-jdbc

# Useful (perhaps) pointers from above links

From https://spring.io/blog/2016/06/13/notes-on-reactive-programming-part-ii-writing-some-code):

Tip 1
> A library that will process sequences for you, like Spring Reactive Web, can handle the subscriptions.
> It’s good to be able to push these concerns down the stack because it saves you from cluttering your code with non-business logic, making it more readable and easier to test and maintain.
> So as a rule, it is a good thing if you can avoid subscribing to a sequence, or at least push that code into a processing layer, and out of the business logic.

Tip 2
> Warning
> 
>  A good rule of thumb is "never call an extractor". 
>  There are some exceptions (otherwise the methods would not exist). 
>  One notable exception is in tests because it’s useful to be able to block to allow results to accumulate.