package org.axonframework.commandhandling.disruptor;

import org.axonframework.commandhandling.annotation.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.repository.Repository;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class DisruptorRepositoryTest {

    private final EventStore eventStore = mock(EventStore.class);

    @Test
    public void testDisruptorCommandBusRepositoryNotAvailableOutsideOfInvokerThread() {
        DisruptorCommandBus commandBus = new DisruptorCommandBus(eventStore);
        Repository<Aggregate> repository = commandBus
                .createRepository(new GenericAggregateFactory<>(Aggregate.class));

        AggregateAnnotationCommandHandler<Aggregate> handler
                = new AggregateAnnotationCommandHandler<>(Aggregate.class, repository);
        handler.subscribe(commandBus);
        DefaultCommandGateway gateway = new DefaultCommandGateway(commandBus);

        // Create the aggregate
        String aggregateId = "" + System.currentTimeMillis();
        gateway.sendAndWait(new CreateCommandAndEvent(aggregateId));

        // Load the aggregate from the repository -- from "worker" thread
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);
        try {
            Aggregate aggregate = repository.load(aggregateId);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("DisruptorCommandBus"));
        } finally {
            uow.rollback();
        }
    }

    public static class CreateCommandAndEvent {

        @TargetAggregateIdentifier
        private final String id;

        public CreateCommandAndEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("serial")
    public static class Aggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        @SuppressWarnings("unused")
        private Aggregate() {
        }

        @CommandHandler
        public Aggregate(CreateCommandAndEvent command) {
            apply(command);
        }

        @EventSourcingHandler
        private void on(CreateCommandAndEvent event) {
            this.id = event.getId();
        }
    }
}
