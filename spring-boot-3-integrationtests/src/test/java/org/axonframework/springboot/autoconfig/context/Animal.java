package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public abstract class Animal {

    @AggregateIdentifier
    protected String aggregateId;
    protected String name;

    @CommandHandler
    public void handle(RenameAnimalCommand command) {
        apply(new AnimalRenamedEvent(command.getAggregateId(), command.getRename()));
    }

    @EventSourcingHandler
    public void on(AnimalRenamedEvent event) {
        this.name = event.getRename();
    }

    public Animal() {
    }
}
