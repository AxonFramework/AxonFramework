package org.axonframework.test;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.junit.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Hierarchy {

    @Test
    public void testFixtureSetupWithAggregateHierarchy() {
        Fixtures.newGivenWhenThenFixture(AbstractAggregate.class)
                .registerAggregateFactory(new AggregateFactory<AbstractAggregate>() {
                    @Override
                    public AbstractAggregate createAggregate(Object aggregateIdentifier,
                                                             DomainEventMessage<?> firstEvent) {
                        return new ConcreteAggregate();
                    }

                    @Override
                    public String getTypeIdentifier() {
                        return "AbstractAggregate";
                    }

                    @Override
                    public Class<AbstractAggregate> getAggregateType() {
                        return AbstractAggregate.class;
                    }
                })
                .given(new MyEvent("123", 0)).when(new TestCommand("123"))
                .expectEvents(new MyEvent("123", 1));
    }

    public static abstract class AbstractAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        public AbstractAggregate() {
        }

        @CommandHandler
        public abstract void handle(TestCommand testCommand);

        @EventSourcingHandler
        protected void on(MyEvent event) {
            this.id = event.getAggregateIdentifier().toString();
        }
    }

    public static class ConcreteAggregate extends AbstractAggregate {

        @Override
        public void handle(TestCommand testCommand) {
            apply(new MyEvent(testCommand.getAggregateIdentifier(), 1));
        }
    }
}
