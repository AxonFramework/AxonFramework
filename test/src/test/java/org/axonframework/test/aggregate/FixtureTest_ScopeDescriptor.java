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
package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.test.matchers.Matchers.*;

/**
 * Test class validating a {@link org.axonframework.messaging.ScopeDescriptor}, specifically an {@link
 * org.axonframework.modelling.command.AggregateScopeDescriptor}, can be resolved on Aggregate's message handling
 * functions.
 *
 * @author Steven van Beelen
 */
class FixtureTest_ScopeDescriptor {

    private FixtureConfiguration<TestAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(TestAggregate.class);
    }

    @Test
    void resolvesScopeDescriptor() {
        fixture.givenNoPriorActivity()
               .when("some-identifier")
               .expectEventsMatching(payloadsMatching(sequenceOf(matches(
                       event -> ScopeDescriptorEvent.class.isAssignableFrom(event.getClass()) &&
                               AggregateScopeDescriptor.class.isAssignableFrom(
                                       ((ScopeDescriptorEvent) event).scopeDescriptor.getClass()
                               )
               ))));
    }

    private static class ScopeDescriptorEvent {

        private final String identifier;
        private final ScopeDescriptor scopeDescriptor;

        private ScopeDescriptorEvent(String identifier, ScopeDescriptor scopeDescriptor) {
            this.identifier = identifier;
            this.scopeDescriptor = scopeDescriptor;
        }
    }

    @SuppressWarnings("unused")
    public static class TestAggregate {

        @SuppressWarnings("FieldCanBeLocal")
        @AggregateIdentifier
        private String identifier;

        @CommandHandler
        public TestAggregate(String identifier, ScopeDescriptor scopeDescriptor) {
            apply(new ScopeDescriptorEvent(identifier, scopeDescriptor));
        }

        @EventSourcingHandler
        public void on(ScopeDescriptorEvent event) {
            identifier = event.identifier;
        }

        public TestAggregate() {
        }
    }
}
