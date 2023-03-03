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
