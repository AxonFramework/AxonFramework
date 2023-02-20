package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test class validating the {@link AbstractAggregateFactory}.
 *
 * @author Steven van Beelen
 */
class AbstractAggregateFactoryTest {

    @Test
    void polymorphicFactoryConstructorBuildsAnticipatedAggregateModel() {
        //noinspection unchecked
        Set<Class<? extends RootAggregate>> subTypes = Sets.newSet(LeafOneAggregate.class, LeafTwoAggregate.class);

        AggregateModel<RootAggregate> expectedAggregateModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subTypes);

        AbstractAggregateFactory<RootAggregate> testSubject = new TestAggregateFactory<>(RootAggregate.class, subTypes);

        AggregateModel<RootAggregate> resultAggregateModel = testSubject.aggregateModel();
        List<Class<?>> resultTypes = resultAggregateModel.types().collect(Collectors.toList());

        expectedAggregateModel.types().map(resultTypes::contains).forEach(Assertions::assertTrue);
    }

    private static class TestAggregateFactory<A> extends AbstractAggregateFactory<A> {

        protected TestAggregateFactory(Class<A> aggregateBaseType, Set<Class<? extends A>> aggregateSubTypes) {
            super(aggregateBaseType, aggregateSubTypes);
        }

        @Override
        protected A doCreateAggregate(String aggregateIdentifier,
                                      @SuppressWarnings("rawtypes") DomainEventMessage firstEvent) {
            return null;
        }
    }

    private static class RootAggregate {

    }

    private static class LeafOneAggregate extends RootAggregate {

    }

    private static class LeafTwoAggregate extends RootAggregate {

    }
}