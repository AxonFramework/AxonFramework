/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
