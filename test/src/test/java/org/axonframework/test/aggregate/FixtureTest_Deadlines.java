/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.test.AxonAssertionError;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class intended to validate all methods in regards to scheduling and validating deadlines.
 *
 * @author Milan Savic
 */
class FixtureTest_Deadlines {

    private static final String AGGREGATE_ID = "id";
    private static final CreateMyAggregateCommand CREATE_COMMAND = new CreateMyAggregateCommand(AGGREGATE_ID);
    private static final int TRIGGER_DURATION_MINUTES = 10;
    private static final String DEADLINE_NAME = "deadlineName";
    private static final String DEADLINE_PAYLOAD = "deadlineDetails";
    private static final String NONE_OCCURRING_DEADLINE_PAYLOAD = "none-occurring-deadline";

    private AggregateTestFixture<MyAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    void expectScheduledDeadline() {
        fixture.givenNoPriorActivity()
               .when(CREATE_COMMAND)
               .expectScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), DEADLINE_PAYLOAD);
    }

    @Test
    void expectScheduledDeadlineOfType() {
        fixture.givenNoPriorActivity()
               .when(CREATE_COMMAND)
               .expectScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class);
    }

    @Test
    void expectScheduledDeadlineWithName() {
        fixture.given(new MyAggregateCreatedEvent(AGGREGATE_ID, DEADLINE_NAME, "deadlineId"))
               .when(new SetPayloadlessDeadlineCommand(AGGREGATE_ID))
               .expectScheduledDeadlineWithName(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "payloadless-deadline");
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expectNoScheduledDeadline() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), DEADLINE_PAYLOAD);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expectNoScheduledDeadlineOfType() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expectNoScheduledDeadlineWithName() {
        fixture.givenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlineWithName(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), DEADLINE_NAME);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expectTriggeredDeadlinesMatching() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlinesMatching(payloadsMatching(exactSequenceOf(deepEquals(DEADLINE_PAYLOAD))));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesMatching() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlinesMatching(payloadsMatching(exactSequenceOf(deepEquals(DEADLINE_PAYLOAD))));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expectTriggeredDeadlines() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlines(DEADLINE_PAYLOAD);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlines() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlines(DEADLINE_PAYLOAD);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesFailsForIncorrectDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlines(NONE_OCCURRING_DEADLINE_PAYLOAD)
        );

        assertTrue(
                result.getMessage().contains("Expected deadlines were not triggered at the given deadline manager.")
        );
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesFailsForIncorrectNumberOfDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlines(DEADLINE_PAYLOAD, NONE_OCCURRING_DEADLINE_PAYLOAD)
        );

        assertTrue(result.getMessage().contains("Got wrong number of triggered deadlines."));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesWithName() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlinesWithName(DEADLINE_NAME);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesWithNameFailsForIncorrectDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlinesWithName(NONE_OCCURRING_DEADLINE_PAYLOAD)
        );

        assertTrue(
                result.getMessage().contains("Expected deadlines were not triggered at the given deadline manager.")
        );
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesWithNameFailsForIncorrectNumberOfDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlinesWithName(DEADLINE_PAYLOAD, NONE_OCCURRING_DEADLINE_PAYLOAD)
        );

        assertTrue(result.getMessage().contains("Got wrong number of triggered deadlines."));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesOfType() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlinesOfType(String.class);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesOfTypeFailsForIncorrectDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlinesOfType(Integer.class)
        );

        assertTrue(
                result.getMessage().contains("Expected deadlines were not triggered at the given deadline manager.")
        );
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void triggeredDeadlinesOfTypeFailsForIncorrectNumberOfDeadlines() {
        ResultValidator<MyAggregate> given =
                fixture.givenNoPriorActivity()
                       .andGivenCommands(CREATE_COMMAND)
                       .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1));

        AxonAssertionError result = assertThrows(
                AxonAssertionError.class,
                () -> given.expectTriggeredDeadlinesOfType(String.class, String.class)
        );

        assertTrue(result.getMessage().contains("Got wrong number of triggered deadlines."));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void deadlineWhichCancelsSchedule() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .when(new ResetTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlines();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void deadlineWhichCancelsAll() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .when(new ResetAllTriggerCommand(AGGREGATE_ID))
               .expectNoScheduledDeadlines();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void deadlineDispatcherInterceptor() {
        fixture.registerDeadlineDispatchInterceptor(
                messages -> (i, m) -> asDeadlineMessage(m.getDeadlineName(), "fakeDeadlineDetails", m.getTimestamp()))
               .givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlines("fakeDeadlineDetails");
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void deadlineHandlerInterceptor() {
        fixture.registerDeadlineHandlerInterceptor((uow, chain) -> {
            uow.transformMessage(deadlineMessage -> asDeadlineMessage(
                    deadlineMessage.getDeadlineName(), "fakeDeadlineDetails", deadlineMessage.getTimestamp())
            );
            return chain.proceedSync();
        })
               .givenNoPriorActivity()
               .andGivenCommands(CREATE_COMMAND)
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectTriggeredDeadlines("fakeDeadlineDetails");
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
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateMyAggregateCommand command, DeadlineManager deadlineManager) {
            String deadlineName = DEADLINE_NAME;
            String deadlineId = deadlineManager.schedule(
                    Duration.ofMinutes(TRIGGER_DURATION_MINUTES), deadlineName, DEADLINE_PAYLOAD
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
