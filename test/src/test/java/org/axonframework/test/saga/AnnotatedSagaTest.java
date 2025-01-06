/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.CallbackBehavior;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.*;
import static org.hamcrest.CoreMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating numerous operations from the {@link SagaTestFixture}.
 *
 * @author Allard Buijze
 */
class AnnotatedSagaTest {

    private static <P> EventMessage<P> asEventMessage(P event) {
        return new GenericEventMessage<>(
                new GenericMessage<>(QualifiedNameUtils.fromClassName(event.getClass()), (P) event),
                () -> GenericEventMessage.clock.instant()
        );
    }

    @Test
    void fixtureApi_AggregatePublishedEvent_NoHistoricActivity() {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectAssociationWith("identifier", "id");
    }

    @Test
    void fixtureApi_AggregatePublishedEventWithMetaData_NoHistoricActivity() {
        String extraIdentifier = UUID.randomUUID().toString();
        Map<String, String> metaData = new HashMap<>();
        metaData.put("extraIdentifier", extraIdentifier);

        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
               .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"), metaData)
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectAssociationWith("identifier", "id")
               .expectAssociationWith("extraIdentifier", extraIdentifier);
    }

    @Test
    void fixtureApi_AggregatePublishedHistoricEventWithMetaData() {
        String extraIdentifier = UUID.randomUUID().toString();
        Map<String, String> metaData = new HashMap<>();
        metaData.put("extraIdentifier", extraIdentifier);

        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenAPublished(new TriggerSagaStartEvent("id"), metaData)
               .whenPublishingA(new TriggerSagaStartEvent("id"))
               .expectActiveSagas(1)
               .expectNoScheduledDeadlines()
               .expectAssociationWith("identifier", "id")
               .expectAssociationWith("extraIdentifier", extraIdentifier);
    }

    @Test
    void fixtureApi_NonTransientResourceInjected() {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerResource(new NonTransientResource());
        fixture.givenNoPriorActivity();
        AssertionError e = assertThrows(AssertionError.class, () ->
                fixture.whenAggregate("id")
                       .publishes(new TriggerSagaStartEvent("id"))
                       .expectNoScheduledDeadlines());
        assertTrue(e.getMessage().contains("StubSaga.nonTransientResource"),
                   "Got unexpected error: " + e.getMessage());
        assertTrue(e.getMessage().contains("transient"), "Got unexpected error: " + e.getMessage());
    }

    @Test
    void fixtureApi_NonTransientResourceInjected_CheckDisabled() {
        FixtureConfiguration fixture = new SagaTestFixture<>(StubSaga.class)
                .withTransienceCheckDisabled();
        fixture.registerResource(new NonTransientResource());
        fixture.givenNoPriorActivity()
               .whenAggregate("id")
               .publishes(new TriggerSagaStartEvent("id"))
               .expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_PublishedEvent_NoHistoricActivity() {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
               .whenPublishingA(new GenericEventMessage<>(
                       new QualifiedName("test", "event", "0.0.1"), new TriggerSagaStartEvent("id")
               ))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", "id")
               .expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_PublishedEventWithMetaData_NoHistoricActivity() {
        String extraIdentifier = UUID.randomUUID().toString();
        Map<String, String> metaData = new HashMap<>();
        metaData.put("extraIdentifier", extraIdentifier);

        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
               .whenPublishingA(new TriggerSagaStartEvent("id"), metaData)
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", "id")
               .expectAssociationWith("extraIdentifier", extraIdentifier)
               .expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_WithApplicationEvents() {
        String aggregate1 = UUID.randomUUID().toString();
        String aggregate2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenAPublished(new TimerTriggeredEvent(UUID.randomUUID().toString()))
               .andThenAPublished(new TimerTriggeredEvent(UUID.randomUUID().toString()))

               .whenPublishingA(new TimerTriggeredEvent(UUID.randomUUID().toString()))

               .expectActiveSagas(0)
               .expectNoAssociationWith("identifier", aggregate2)
               .expectNoAssociationWith("identifier", aggregate1)
               .expectNoScheduledEvents()
               .expectNoScheduledDeadlines()
               .expectDispatchedCommands()
               .expectPublishedEvents();
    }

    @Test
    void fixtureApi_WhenEventIsPublishedToEventBus() {
        String aggregate1 = UUID.randomUUID().toString();
        String aggregate2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(new TriggerSagaStartEvent(aggregate1),
                                                      new TriggerExistingSagaEvent(aggregate1))
                .whenAggregate(aggregate1).publishes(new TriggerExistingSagaEvent(aggregate1));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate1);
        validator.expectNoAssociationWith("identifier", aggregate2);
        validator.expectScheduledEventMatching(Duration.ofMinutes(10),
                                               Matchers.messageWithPayload(CoreMatchers.any(Object.class)));
        validator.expectDispatchedCommands();
        validator.expectPublishedEventsMatching(listWithAnyOf(messageWithPayload(any(SagaWasTriggeredEvent.class))));
        validator.expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_ElapsedTimeBetweenEventsHasEffectOnScheduler() throws Exception {
        String aggregate1 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        FixtureExecutionResult validator = fixture
                // event schedules a TriggerEvent after 10 minutes from t0
                .givenAggregate(aggregate1).published(new TriggerSagaStartEvent(aggregate1))
                // time shifts to t0+5
                .andThenTimeElapses(Duration.ofMinutes(5))
                // reset event schedules a TriggerEvent after 10 minutes from t0+5
                .andThenAggregate(aggregate1).published(new ResetTriggerEvent(aggregate1))
                // when time shifts to t0+10
                .whenTimeElapses(Duration.ofMinutes(6));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate1);
        // 6 minutes have passed since the 10minute timer was reset,
        // so expect the timer to be scheduled for 4 minutes (t0 + 15)
        validator.expectScheduledEventMatching(Duration.ofMinutes(4),
                                               Matchers.messageWithPayload(CoreMatchers.any(Object.class)));
        validator.expectNoDispatchedCommands();
        validator.expectPublishedEvents();
        validator.expectNoScheduledDeadlines();
    }


    @Test
    void fixtureApi_givenCurrentTime() {
        String identifier = UUID.randomUUID().toString();
        Instant fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS);
        Instant fourDaysMinusTenMinutesAgo = fourDaysAgo.plus(10, ChronoUnit.MINUTES);

        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenCurrentTime(fourDaysAgo)
               .whenPublishingA(new TriggerSagaStartEvent(identifier))
               .expectScheduledEvent(fourDaysMinusTenMinutesAgo, new TimerTriggeredEvent(identifier))
               .expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_WhenTimeElapses_UsingDefaults() {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))
               .whenTimeElapses(Duration.ofMinutes(35))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectNoScheduledDeadlines()
               // since we return null for the command, the other is never sent...
               .expectDispatchedCommands("Say hi!")
               .expectPublishedEventsMatching(noEvents());
    }

    @Test
    void fixtureApi_WhenTimeElapses_UsingCallbackBehavior() throws Exception {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        CallbackBehavior commandHandler = mock(CallbackBehavior.class);
        when(commandHandler.handle(eq("Say hi!"), isA(MetaData.class))).thenReturn("Hi again!");
        fixture.setCallbackBehavior(commandHandler);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))
               .whenTimeElapses(Duration.ofMinutes(35))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectNoScheduledDeadlines()
               .expectDispatchedCommands("Say hi!", "Hi again!")
               .expectPublishedEventsMatching(noEvents());

        verify(commandHandler, times(2)).handle(isA(Object.class), eq(MetaData.emptyInstance()));
    }

    @Test
    void fixtureApi_WhenTimeAdvances() {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))

               .whenTimeAdvancesTo(Instant.now().plus(Duration.ofDays(1)))

               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectNoScheduledDeadlines()
               .expectDispatchedCommands("Say hi!");
    }

    @Test
    void publishEventFromSecondFixtureCall() {
        String identifier = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.whenAggregate(identifier).publishes(new TriggerSagaStartEvent(identifier))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectPublishedEvents();
        fixture.whenAggregate(identifier).publishes(new TriggerExistingSagaEvent(identifier))
               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectPublishedEventsMatching(
                       payloadsMatching(exactSequenceOf(any(SagaWasTriggeredEvent.class), andNoMore()))
               );
    }

    @Test
    void noExceptionThrownInHandlerMethod() {
        String identifier = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);

        FixtureExecutionResult fixtureExecutionResult = fixture.givenNoPriorActivity().whenAggregate(identifier)
                                                               .publishes(new TriggerSagaStartEvent(identifier));
        assertDoesNotThrow(fixtureExecutionResult::expectSuccessfulHandlerExecution);
    }

    @Test
    void exceptionThrownInHandlerMethod() {
        String identifier = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerListenerInvocationErrorHandler((exception, event, eventHandler) -> {/* No-op */});

        FixtureExecutionResult fixtureExecutionResult =
                fixture.givenAPublished(new TriggerSagaStartEvent(identifier))
                       .whenAggregate(identifier)
                       .publishes(new TriggerExceptionWhileHandlingEvent(identifier));
        assertThrows(AxonAssertionError.class, fixtureExecutionResult::expectSuccessfulHandlerExecution);
    }

    @Test
    void fixtureApi_WhenEventOccurs() {
        String aggregate1 = UUID.randomUUID().toString();
        String aggregate2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(
                        asEventMessage(new TriggerSagaStartEvent(aggregate1)),
                        new TriggerExistingSagaEvent(aggregate1))
                .andThenAggregate(aggregate2).published(new TriggerSagaStartEvent(aggregate2))
                .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent(aggregate1));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate2);
        validator.expectNoAssociationWith("identifier", aggregate1);
        validator.expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(Duration.ofMinutes(10), messageWithPayload(CoreMatchers.any(
                TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(Duration.ofMinutes(10), new TimerTriggeredEvent(aggregate1));
        validator.expectScheduledEventOfType(fixture.currentTime().plusSeconds(600), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(fixture.currentTime().plusSeconds(600),
                                               messageWithPayload(CoreMatchers.any(TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(fixture.currentTime().plusSeconds(600),
                                       new TimerTriggeredEvent(aggregate1));
        validator.expectDispatchedCommands();
        validator.expectNoDispatchedCommands();
        validator.expectPublishedEventsMatching(noEvents());
        validator.expectNoScheduledDeadlines();
    }

    @Test
    void fixtureApi_DomainEventMessageIsAssignableFromMessage() {
        String aggregate1 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(
                        asEventMessage(new TriggerSagaStartEvent(aggregate1)),
                        new TriggerExistingSagaEvent(aggregate1),
                        new TriggerAssociationResolverSagaEvent(aggregate1))
                .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent(aggregate1));

        validator.expectActiveSagas(0);
    }
}
