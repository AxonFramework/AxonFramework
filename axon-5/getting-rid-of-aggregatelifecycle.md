# Let's get rid of the static AggregateLifecycle methods

## The problem

Axon 4 currently relies on a static `AggregateLifecycle.appl()` method to apply events to the state of an aggregate, and
then publish them to the event store. These static methods are intrusive in the domain logic, and don't make too much 
sense on a conceptual level.

## Requirements for a solution

 - No static methods
 - No explicit or implicit coupling between various components (such as sharing the key based on which resources are made available in a ProcessingContext)

## The solution

When a command handling component needs one or more event-sourced models, the infrastructure components that create these
models will need to open an event stream to load relevant events. The event store should, instead of returning the events 
and closing the stream, keep the stream open, and provide a soft "end of stream" message when all relevant events are 
loaded from the event store. Once events are published, the event store should check if there are any open streams in the
current processing context. If that is the case, it should publish those events onto those streams to allow the components
that loaded the model(s) to update these models.

