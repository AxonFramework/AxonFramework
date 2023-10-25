package org.axonframework.messaging;

/**
 * @author Milan Savic
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
public interface MessageBus {

    // a. publish
    // b. subscribe

    // inspiration <=> steal
    // This is what RSocket does!!!
    // 1. fire and forget (publish)              ->
    // send == 1 && receive == 0
    void fireAndForget(Object message, RoutingStrategy strategy);

    // 2. request/response                      <->
    // send == 1 && receive == 1

    // 3. subscription                          <==
    // send == 1 && receive == 0..N

    // 4. bi-directional subscription           <==>
    // send == 1..N && receive == 0..N

    // Message Handler  == return 0..N
    // Command Handler  == return 0..1
    // Event Handler    == return 0
    // Query Handler    == return 1..N

    interface CommandBus {

        // Impl. contains:
        // ==> DispatchBus
        // <== ResponseBus

        // Uses MessageBus#fireAndForget with consistent hashing
        // new GrpcMessageBus(new ConsistentHashRoutingStrategy());
    }

    // HandlerDiscoveryStrategy?
    interface RoutingStrategy {

    }
    interface ConsistentHashing extends RoutingStrategy {
        // Command-styled comms?
    }
    interface Broadcast extends RoutingStrategy {
        // Event-styled publish?
    }
    interface RoundRobin extends RoutingStrategy {

    }

    interface ConsistentRoutingMessageBus {

    }
    // Current CommandBus
    // 1. and 2. WITH Consistent Hashing

    // EventBus
    // QueryBus
}
