package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.Random;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Dog extends Animal {

    // Field present to have the Cat aggregate differ in structure from the Dog for snapshot testing.
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private int ageInYears;

    @CommandHandler
    public Dog(CreateDogCommand command) {
        apply(new DogCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(DogCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
        this.ageInYears = new Random().nextInt(7);
    }

    public Dog() {
        // Required by Axon
    }
}
