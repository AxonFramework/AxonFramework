/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * @author Allard Buijze
 */
class FixtureTest_Hierarchy {

    @Test
    void fixtureSetupWithAggregateHierarchy() {
        new AggregateTestFixture<>(AbstractAggregate.class)
                .registerAggregateFactory(new AggregateFactory<AbstractAggregate>() {
                    @Override
                    public AbstractAggregate createAggregateRoot(String aggregateIdentifier, DomainEventMessage<?> firstEvent) {
                        return new ConcreteAggregate();
                    }

                    @Override
                    public Class<AbstractAggregate> getAggregateType() {
                        return AbstractAggregate.class;
                    }
                })
                .given(new MyEvent("123", 0)).when(new TestCommand("123"))
                .expectEvents(new MyEvent("123", 1));
    }

    public static abstract class AbstractAggregate {

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
