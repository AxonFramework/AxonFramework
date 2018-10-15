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
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.*;

import java.time.Duration;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

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
    public void testDeadlineWhichCancelsSchedule() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .when(new ResetTriggerCommand("id"))
               .expectNoScheduledDeadlines();
    }

    @Test
    public void testDeadlineWhichCancelsAll() {
        fixture.givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .when(new ResetAllTriggerCommand("id"))
               .expectNoScheduledDeadlines();
    }

    @Test
    public void testDeadlineDispatcherInterceptor() {
        fixture.registerDeadlineDispatchInterceptor(
                messages -> (i, m) -> GenericDeadlineMessage
                        .asDeadlineMessage(m.getDeadlineName(), "fakeDeadlineDetails"))
               .givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .andThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectDeadlinesMet("fakeDeadlineDetails");
    }

    @Test
    public void testDeadlineHandlerInterceptor() {
        fixture.registerDeadlineHandlerInterceptor((uow, chain) -> {
                    uow.transformMessage(deadlineMessage -> GenericDeadlineMessage
                            .asDeadlineMessage(deadlineMessage.getDeadlineName(), "fakeDeadlineDetails"));
                    return chain.proceed();
                })
               .givenNoPriorActivity()
               .andGivenCommands(new CreateMyAggregateCommand("id"))
               .andThenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
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

        @TargetAggregateIdentifier
        private final String aggregateId;

        private ResetTriggerCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    @SuppressWarnings("unused")
    private static class ResetAllTriggerCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private ResetAllTriggerCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    @SuppressWarnings("unused")
    private static class MyAggregate {

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

        @EventSourcingHandler
        public void on(MyAggregateCreatedEvent event) {
            this.id = event.aggregateId;
            this.deadlineName = event.deadlineName;
            this.deadlineId = event.deadlineId;
        }

        @CommandHandler
        public void handle(ResetTriggerCommand command, DeadlineManager deadlineManager) {
            deadlineManager.cancelSchedule(deadlineName, deadlineId);
        }

        @CommandHandler
        public void handle(ResetAllTriggerCommand command, DeadlineManager deadlineManager) {
            deadlineManager.cancelAll(deadlineName);
        }

        @DeadlineHandler
        public void handleDeadline(String deadlineInfo) {
            // Nothing to be done for test purposes, having this deadline handler invoked is sufficient
        }
    }
}
