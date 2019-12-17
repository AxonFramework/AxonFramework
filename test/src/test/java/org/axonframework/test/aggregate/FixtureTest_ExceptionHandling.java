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
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.Test;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixtureTest_ExceptionHandling {

    private final FixtureConfiguration<MyAggregate> fixture = new AggregateTestFixture<>(MyAggregate.class);

    @Test
    void testCreateAggregate() {
        fixture.givenCommands().when(
                new CreateMyAggregateCommand("14")
        ).expectEvents(
                new MyAggregateCreatedEvent("14")
        );
    }

    @Test
    void givenUnknownCommand() {
        FixtureExecutionException fee = assertThrows(FixtureExecutionException.class, () -> fixture.givenCommands(
                new CreateMyAggregateCommand("14"),
                new UnknownCommand("14")
        ));
        assertEquals(NoHandlerForCommandException.class, fee.getCause().getClass());
    }

    @Test
    void testWhenExceptionTriggeringCommand() {
        fixture.givenCommands(new CreateMyAggregateCommand("14")).when(
                new ExceptionTriggeringCommand("14")
        ).expectException(RuntimeException.class);
    }

    @Test
    void testGivenExceptionTriggeringCommand() {
        assertThrows(RuntimeException.class, () ->
                        fixture.givenCommands(
                                new CreateMyAggregateCommand("14"),
                                new ExceptionTriggeringCommand("14")
                        )
                );
    }

    @Test
    void testGivenCommandWithInvalidIdentifier() {
        fixture.givenCommands(
                new CreateMyAggregateCommand("1")
        ).when(
                new ValidMyAggregateCommand("2")
        ).expectException(EventStoreException.class);
    }

    @Test
    void testExceptionMessageCheck() {
        fixture.givenCommands(
                new CreateMyAggregateCommand("1")
        ).when(
                new ValidMyAggregateCommand("2")
        ).expectException(EventStoreException.class)
                .expectExceptionMessage("You probably want to use aggregateIdentifier() on your fixture to get the aggregate identifier to use");
    }

    @Test
    void testExceptionMessageCheckWithMatcher() {
        fixture.givenCommands(
                new CreateMyAggregateCommand("1")
        ).when(
                new ValidMyAggregateCommand("2")
        ).expectException(EventStoreException.class)
                .expectExceptionMessage(containsString("You"));
    }

    @Test
    void testWhenCommandWithInvalidIdentifier() {
        assertThrows(FixtureExecutionException.class, () ->
                fixture.givenCommands(
                        new CreateMyAggregateCommand("1"),
                        new ValidMyAggregateCommand("2")
                )
        );
    }

    private static abstract class AbstractMyAggregateCommand {
        @TargetAggregateIdentifier
        public final String id;

        protected AbstractMyAggregateCommand(String id) {
            this.id = id;
        }
    }

    private static class CreateMyAggregateCommand extends AbstractMyAggregateCommand {
        protected CreateMyAggregateCommand(String id) {
            super(id);
        }
    }

    private static class ExceptionTriggeringCommand extends AbstractMyAggregateCommand {
        protected ExceptionTriggeringCommand(String id) {
            super(id);
        }
    }

    private static class ValidMyAggregateCommand extends AbstractMyAggregateCommand {
        protected ValidMyAggregateCommand(String id) {
            super(id);
        }
    }

    private static class UnknownCommand extends AbstractMyAggregateCommand {
        protected UnknownCommand(String id) {
            super(id);
        }
    }

    private static class MyAggregateCreatedEvent {
        public final String id;

        public MyAggregateCreatedEvent(String id) {
            this.id = id;
        }
    }

    private static class MyAggregate {
        @AggregateIdentifier
        String id;

        private MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand cmd) {
            apply(new MyAggregateCreatedEvent(cmd.id));
        }

        @CommandHandler
        public void handle(ValidMyAggregateCommand cmd) {
            /* no-op */
        }

        @CommandHandler
        public void handle(ExceptionTriggeringCommand cmd) {
            throw new RuntimeException("Error");
        }

        @EventHandler
        private void on(MyAggregateCreatedEvent event) {
            this.id = event.id;
        }
    }
}
