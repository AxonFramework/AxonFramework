package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Cat extends Animal {

    @CommandHandler
    public Cat(CreateCatCommand command) {
        apply(new CatCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(CatCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
    }

    public Cat() {
        // Required by Axon
    }
}
