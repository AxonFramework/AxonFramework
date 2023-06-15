package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Cat extends Animal {

    // Field present to have the Dog aggregate differ in structure from the Cat for snapshot testing.
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private String hairColor;

    @CommandHandler
    public Cat(CreateCatCommand command) {
        apply(new CatCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(CatCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
        this.hairColor = UUID.randomUUID().toString();
    }

    public Cat() {
        // Required by Axon
    }
}
