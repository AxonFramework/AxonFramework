/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.repository.Repository;
import org.junit.*;

import java.util.UUID;

import static org.axonframework.test.Fixtures.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest {

    @Test
    public void testFixture() {
        givenWhenThenFixture()
                .registerAnnotatedCommandHandler(new MyCommandHandler(genericRepository(MyAggregate.class)))
                .given(new MyEvent(1), new MyEvent(2))
                .when(new TestCommand())
                .expectReturnValue(Void.TYPE)
                .expectEvents(new MyEvent(3));
    }

    @Test
    public void testFirstFixture() {
        givenWhenThenFixture()
                .registerAnnotatedCommandHandler(new MyCommandHandler(genericRepository(MyAggregate.class)))
                .given(new MyEvent(1))
                .when(new TestCommand())
                .expectReturnValue(Void.TYPE)
                .expectEvents(new MyEvent(2));
    }

    @Test
    public void testFixture2() {
        givenWhenThenFixture()
                .registerAnnotatedCommandHandler(new MyCommandHandler(genericRepository(MyAggregate.class)))
                .given(new MyEvent(1), new MyEvent(2), new MyEvent(3))
                .when(new TestCommand())
                .expectEvents(new MyEvent(4))
                .expectReturnValue(Void.TYPE);
    }

    public static class MyAggregate extends AbstractAnnotatedAggregateRoot {

        private int lastNumber;

        public MyAggregate() {
        }

        public MyAggregate(UUID aggregateIdentifier) {
            super(aggregateIdentifier);
        }

        @EventHandler
        public void handleMyEvent(MyEvent event) {
            lastNumber = event.someValue;
        }

        @EventHandler
        public void handleAll(DomainEvent event) {
            // we don't care about events
        }

        public void doSomething() {
            apply(new MyEvent(lastNumber + 1));
        }
    }

    private class MyCommandHandler {

        private final Repository<MyAggregate> repository;

        public MyCommandHandler(Repository<MyAggregate> repository) {
            this.repository = repository;
        }

        @CommandHandler
        public void handleTestCommand(TestCommand testCommand) {
            MyAggregate aggregate = repository.load(aggregateIdentifier());
            aggregate.doSomething();
            repository.save(aggregate);
        }
    }

    private class TestCommand {

    }

    public static class MyEvent extends DomainEvent {

        private int someValue;

        public MyEvent(int someValue) {
            this.someValue = someValue;
        }

    }
}
