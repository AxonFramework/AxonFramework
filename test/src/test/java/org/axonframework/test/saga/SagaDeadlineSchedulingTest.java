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

package org.axonframework.test.saga;

import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Tests for scheduling deadlines on {@link SagaTestFixture}.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public class SagaDeadlineSchedulingTest {

    private static final int TRIGGER_DURATION_MINUTES = 10;

    private SagaTestFixture<MySaga> fixture;

    @Before
    public void setUp() {
        fixture = new SagaTestFixture<>(MySaga.class);
    }

    @Test
    public void testDeadlineScheduling() {
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectScheduledDeadline(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), "deadlineDetails")
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineSchedulingTypeMatching() {
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectScheduledDeadlineOfType(Duration.ofMinutes(TRIGGER_DURATION_MINUTES), String.class)
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineMet() {
        fixture.givenAggregate("id").published(new TriggerSagaStartEvent("id"))
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectActiveSagas(1)
               .expectDeadlinesMet("deadlineDetails")
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineCancelled() {
        fixture.givenAggregate("id")
               .published(new TriggerSagaStartEvent("id"))
               .whenPublishingA(new ResetTriggerEvent("id"))
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineWhichCancelsAll() {
        fixture.givenAggregate("id")
               .published(new TriggerSagaStartEvent("id"))
               .whenPublishingA(new ResetAllTriggeredEvent("id"))
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineDispatchInterceptor() {
        fixture.registerDeadlineDispatchInterceptor(
                messages -> (i, m) -> GenericDeadlineMessage
                        .asDeadlineMessage(m.getDeadlineName(), "fakeDeadlineDetails"))
               .givenAggregate("id").published(new TriggerSagaStartEvent("id"))
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectActiveSagas(1)
               .expectDeadlinesMet("fakeDeadlineDetails")
               .expectNoScheduledEvents();
    }

    @Test
    public void testDeadlineHandlerInterceptor() {
        fixture.registerDeadlineHandlerInterceptor((uow, chain) -> {
                    uow.transformMessage(deadlineMessage -> GenericDeadlineMessage
                            .asDeadlineMessage(deadlineMessage.getDeadlineName(), "fakeDeadlineDetails"));
                    return chain.proceed();
                })
               .givenAggregate("id").published(new TriggerSagaStartEvent("id"))
               .whenTimeElapses(Duration.ofMinutes(TRIGGER_DURATION_MINUTES + 1))
               .expectActiveSagas(1)
               .expectDeadlinesMet("fakeDeadlineDetails")
               .expectNoScheduledEvents();
    }

    private static class ResetAllTriggeredEvent {

        private final String identifier;

        private ResetAllTriggeredEvent(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    @SuppressWarnings("unused")
    public static class MySaga {

        private String deadlineId;
        private String deadlineName;

        @StartSaga
        @SagaEventHandler(associationProperty = "identifier")
        public void on(TriggerSagaStartEvent event, @Timestamp Instant timestamp, DeadlineManager deadlineManager) {
            deadlineName = "deadlineName";
            deadlineId = deadlineManager.schedule(
                    Duration.ofMinutes(TRIGGER_DURATION_MINUTES), deadlineName, "deadlineDetails"
            );
        }

        @SagaEventHandler(associationProperty = "identifier")
        public void on(ResetTriggerEvent event, DeadlineManager deadlineManager) {
            deadlineManager.cancelSchedule(deadlineName, deadlineId);
        }

        @SagaEventHandler(associationProperty = "identifier")
        public void on(ResetAllTriggeredEvent event, DeadlineManager deadlineManager) {
            deadlineManager.cancelAll(deadlineName);
        }

        @DeadlineHandler
        public void handleDeadline(String deadlineInfo) {
            // Nothing to be done for test purposes, having this deadline handler invoked is sufficient
        }
    }
}
