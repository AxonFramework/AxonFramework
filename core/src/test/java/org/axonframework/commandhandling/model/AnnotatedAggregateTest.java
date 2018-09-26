package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.*;
import org.mockito.*;

import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

public class AnnotatedAggregateTest {

    private final String ID = "id";
    private Repository<AggregateRoot> repository;
    private EventBus eventBus;

    @Before
    public void setUp() {
        eventBus = mock(EventStore.class);
        repository = StubRepository.builder().eventBus(eventBus).build();
    }

    @Test
    public void testApplyingEventInHandlerPublishesInRightOrder() throws Exception {
        Command command = new Command(ID);
        DefaultUnitOfWork<CommandMessage<Object>> uow = DefaultUnitOfWork.startAndGet(asCommandMessage(command));
        Aggregate<AggregateRoot> aggregate = uow.executeWithResult(() -> repository
                .newInstance(() -> new AggregateRoot(command)));
        assertNotNull(aggregate);

        InOrder inOrder = inOrder(eventBus);
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_1.class
                .equals(x.getPayloadType())));
        inOrder.verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<?>>) x -> Event_2.class
                .equals(x.getPayloadType())));
    }

    private static class Command {

        private final String id;

        private Command(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class Event_1 {

        private final String id;

        private Event_1(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class Event_2 {

        private final String id;

        public Event_2(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class AggregateRoot {

        @AggregateIdentifier
        private String id;

        public AggregateRoot() {
        }

        @CommandHandler
        public AggregateRoot(Command command) {
            apply(new Event_1(command.getId()));
        }

        @EventSourcingHandler
        public void on(Event_1 event) {
            this.id = event.getId();
            apply(new Event_2(event.getId()));
        }

        @EventSourcingHandler
        public void on(Event_2 event) {
        }
    }

    private static class StubRepository extends AbstractRepository<AggregateRoot, Aggregate<AggregateRoot>> {

        private final EventBus eventBus;

        private StubRepository(Builder builder) {
            super(builder);
            this.eventBus = builder.eventBus;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected Aggregate<AggregateRoot> doCreateNew(Callable<AggregateRoot> factoryMethod) throws Exception {
            return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), eventBus);
        }

        @Override
        protected void doSave(Aggregate<AggregateRoot> aggregate) {

        }

        @Override
        protected Aggregate<AggregateRoot> doLoad(String aggregateIdentifier, Long expectedVersion) {
            return null;
        }

        @Override
        protected void doDelete(Aggregate<AggregateRoot> aggregate) {

        }

        private static class Builder extends AbstractRepository.Builder<AggregateRoot> {

            private EventBus eventBus;

            private Builder() {
                aggregateType(AggregateRoot.class);
            }

            public Builder eventBus(EventBus eventBus) {
                this.eventBus = eventBus;
                return this;
            }

            public StubRepository build() {
                return new StubRepository(this);
            }
        }
    }
}
