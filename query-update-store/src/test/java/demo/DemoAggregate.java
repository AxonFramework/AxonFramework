package demo;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Slf4j @Aggregate public class DemoAggregate {

    @AggregateIdentifier String aggId;

    @CommandHandler public DemoAggregate(DemoCommand cmd) {
        log.error("aggr: " + cmd);
        apply(new DemoEvent(cmd.getAggId(), cmd.getSagaId()));
    }

    @EventSourcingHandler public void handle(DemoEvent evt) {
        log.error("aggr: " + evt);
        this.aggId = evt.getAggId();
    }
}
