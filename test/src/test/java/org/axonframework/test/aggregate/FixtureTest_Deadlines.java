/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Test class intended to validate all methods in regards to scheduling and validating deadlines.
 *
 * @author Milan Savic
 */
class FixtureTest_Deadlines {

    private static final String AGGREGATE_ID = "id";
    private static final CreateMyAggregateCommand CREATE_COMMAND = new CreateMyAggregateCommand(AGGREGATE_ID);
    private static final int TRIGGER_DURATION_MINUTES = 10;

    private AggregateTestFixture<MyAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    void testExpectScheduledDeadline() {
        fixture.givenNoPriorActivity()
               .when(CREATE_COMMAND)
               .expectScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineDetails");
    }

    @Test
    void testExpectScheduledDeadlineOfType() {
        fixture.givenNoPriorActivity()
               .when(CREATE_COMMAND)
               .expectScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class);
    }

    @Test
    void testExpectScheduledDeadlineWithName() {
        fixture.given(new MyAggregateCreatedEvent(AGGREGATE_ID, "deadlineName", "deadlineId"))
               .when(new SetPayloadlessDeadlineCommand(AGGREGATE_ID))
               .expectScheduledDeadlineWithName(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "payloadless-deadline");
    }

    @Test
    void testExpectNoScheduledDeadline() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineDetails");
    }

    @Test
    void testExpectNoScheduledDeadlineOfType() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class);
    }

    @Test
    void testExpectNoScheduledDeadlineWithName() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlineWithName(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineName");
    }

    @Test
    void testDeadlineMet() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectDeadlinesMet("deadlineDetails");
    }

    @Test
    void testDeadlineWhichCancelsSchedule() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlines();
    }

    @Test
    void testDeadlineWhichCancelsAll() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .when(new ResetAllTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlines();
    }

    @Test
    void testDeadlineDispatcherInterceptor() {
        fixture.registerDeadlineDispatchInterceptor(
                messages -> (i, m) -> asDeadlineMessage(m.getDeadlineName(), "fakeDeadlineDetails", m.getTimestamp()))
               .givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectDeadlinesMet("fakeDeadlineDetails");
    }

    @Test
    void testDeadlineHandlerInterceptor() {
        fixture.registerDeadlineHandlerInterceptor((uow, chain) -> {
            uow.transformMessage(deadlineMessage -> asDeadlineMessage(
                    deadlineMessage.getDeadlineName(), "fakeDeadlineDetails", deadlineMessage.getTimestamp())
            );
            return chain.proceed();
        })
               .givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectDeadlinesMet("fakeDeadlineDetails");
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
        private final String deadlineName;
        private final String deadlineId;

        private MyAggregateCreatedEvent(String aggregateId, String deadlineName, String deadlineId) {
            this.aggregateId = aggregateId;
            this.deadlineName = deadlineName;
            this.deadlineId = deadlineId;
        }
    }

    @SuppressWarnings("unused")
    private static class ResetTriggerCommand {

        @SuppressWarnings("FieldCanBeLocal")
        @TargetAggregateIdentifier
        private final String aggregateId;

        private ResetTriggerCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    @SuppressWarnings("unused")
    private static class ResetAllTriggerCommand {

        @SuppressWarnings("FieldCanBeLocal")
        @TargetAggregateIdentifier
        private final String aggregateId;

        private ResetAllTriggerCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    private static class SetPayloadlessDeadlineCommand {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @TargetAggregateIdentifier
        private final String aggregateId;

        private SetPayloadlessDeadlineCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregate {

        @SuppressWarnings("FieldCanBeLocal")
        @AggregateIdentifier
        private String id;
        private String deadlineName;
        private String deadlineId;

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(CreateMyAggregateCommand command, DeadlineManager deadlineManager) {
            String deadlineName = "deadlineName";
            String deadlineId = deadlineManager.schedule(
                    Duration.ofMinutes(TRIGGER_DURATION_MINUTES), deadlineName, "deadlineDetails"
            );
            apply(new MyAggregateCreatedEvent(command.aggregateId, deadlineName, deadlineId));
        }

        @CommandHandler
        public void handle(ResetTriggerCommand command, DeadlineManager deadlineManager) {
            deadlineManager.cancelSchedule(deadlineName, deadlineId);
        }

        @CommandHandler
        public void handle(ResetAllTriggerCommand command, DeadlineManager deadlineManager) {
            deadlineManager.cancelAll(deadlineName);
        }

        @CommandHandler
        public void handle(SetPayloadlessDeadlineCommand command, DeadlineManager deadlineManager) {
            deadlineManager.schedule(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "payloadless-deadline");
        }

        @DeadlineHandler
        public void handleDeadline(String deadlineInfo) {
            // Nothing to be done for test purposes, having this deadline handler invoked is sufficient
        }

        @DeadlineHandler(deadlineName = "payloadless-deadline")
        public void handle() {
            // Nothing to be done for test purposes, having this deadline handler invoked is sufficient
        }

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.aggregateId;
            this.deadlineName = event.deadlineName;
            this.deadlineId = event.deadlineId;
        }
    }
}
