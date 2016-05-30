package org.axonframework.commandhandling.disruptor;

import org.axonframework.commandhandling.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Test;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class DisruptorRepositoryTest {

    private final EventStore eventStore = mock(EventStore.class);
    private final EventBus eventBus = mock(EventBus.class);

    @Test
    public void testDisruptorCommandBusRepositoryNotAvailableOutsideOfInvokerThread() {
        DisruptorCommandBus commandBus = new DisruptorCommandBus(eventStore);
        Repository<TestAggregate> repository = commandBus
                .createRepository(new GenericAggregateFactory<>(TestAggregate.class));

        AggregateAnnotationCommandHandler<TestAggregate> handler
                = new AggregateAnnotationCommandHandler<>(TestAggregate.class, repository);
        handler.subscribe(commandBus);
        DefaultCommandGateway gateway = new DefaultCommandGateway(commandBus);

        // Create the aggregate
        String aggregateId = "" + System.currentTimeMillis();
        gateway.sendAndWait(new CreateCommandAndEvent(aggregateId));

        // Load the aggregate from the repository -- from "worker" thread
        UnitOfWork<CommandMessage<?>> uow = DefaultUnitOfWork.startAndGet(null);
        try {
            Aggregate<TestAggregate> aggregate = repository.load(aggregateId);
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
    public static class TestAggregate {

        @AggregateIdentifier
        private String id;

        @SuppressWarnings("unused")
        private TestAggregate() {
        }

        @CommandHandler
        public TestAggregate(CreateCommandAndEvent command) {
            apply(command);
        }

        @EventSourcingHandler
        private void on(CreateCommandAndEvent event) {
            this.id = event.getId();
        }
    }
}
