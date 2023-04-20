package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Dog extends Animal {

    @CommandHandler
    public Dog(CreateDogCommand command) {
        apply(new DogCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(DogCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
    }

    public Dog() {
        // Required by Axon
    }
}
