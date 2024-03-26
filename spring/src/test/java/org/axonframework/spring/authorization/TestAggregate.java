package org.axonframework.spring.authorization;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Test Aggregate
 *
 * @author Roald Bankras
 */
@Aggregate
public class TestAggregate {

    @AggregateIdentifier
    private UUID aggregateId;

    public TestAggregate() {
    }

    @CommandHandler
    public TestAggregate(CreateAggregateCommand cmd) {
        apply(new AggregateCreatedEvent(cmd.getAggregateId()));
    }

    @EventSourcingHandler
    public void on(AggregateCreatedEvent evt) {
        aggregateId = evt.getAggregateId();
    }
}

