/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.*;

import java.time.Duration;

import static org.axonframework.commandhandling.model.AggregateLifecycle.*;

/**
 * Tests for scheduling deadlines on {@link AggregateTestFixture}.
 *
 * @author Milan Savic
 */
public class AggregateDeadlineSchedulingTest {

    private static final int TRIGGER_DURATION_MINUTES = 10;

    private AggregateTestFixture<MyAggregate> fixture;

    @Before
    public void setUp() {
        fixture = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    public void testDeadlineScheduling() {
        fixture.givenNoPriorActivity()
               .when(new CreateMyAggregateCommand("id"))
               .expectScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineDetails");
    }

    @Test
    public void testDeadlineSchedulingTypeMatching() {
        fixture.givenNoPriorActivity()
               .when(new CreateMyAggregateCommand("id"))
               .expectScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class);
    }

    @Test
    public void testDeadlineMet() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .andThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectDeadlinesMet("deadlineDetails");
    }

    @Test
    public void testDeadlineCancelled() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .when(new ResetTriggerCommand("id"))
               .expectNoScheduledDeadlines();
    }

    private static class CreateMyAggregateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private CreateMyAggregateCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    private static class MyAggregateCreatedEvent {

        private final String aggregateId;
        private final ScheduleToken scheduleToken;

        private MyAggregateCreatedEvent(String aggregateId,
                                        ScheduleToken scheduleToken) {
            this.aggregateId = aggregateId;
            this.scheduleToken = scheduleToken;
        }
    }

    private static class ResetTriggerCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private ResetTriggerCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregate {

        @AggregateIdentifier
        private String id;
        private ScheduleToken scheduleToken;

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command) {
            ScheduleToken scheduleToken = scheduleDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES),
                                                           "deadlineDetails");
            apply(new MyAggregateCreatedEvent(command.aggregateId, scheduleToken));
        }

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.aggregateId;
            this.scheduleToken = event.scheduleToken;
        }

        @CommandHandler
        public void handle(ResetTriggerCommand command) {
            cancelDeadline(scheduleToken);
        }

        @DeadlineHandler
        public void handleDeadline(String deadlineInfo) {
            // nothing to be done for test purposes, having this deadline handler invoked is sufficient
        }
    }
}
