/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.utils.CallbackBehavior;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.*;
import static org.hamcrest.CoreMatchers.any;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    @Test
    public void testFixtureApi_WhenEventOccurs() throws Exception {
        String aggregate1 = UUID.randomUUID().toString();
        String aggregate2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        FixtureExecutionResult validator = fixture
                .givenAggregate(aggregate1).published(
                        GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(aggregate1)),
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
    }

    @Test
    public void testFixtureApi_AggregatePublishedEvent_NoHistoricActivity() throws Exception {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
                .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", "id");
    }

    @Test
    public void testFixtureApi_NonTransientResourceInjected() throws Exception {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerResource(new NonTransientResource());
        fixture.givenNoPriorActivity();
        try {
            fixture.whenAggregate("id").publishes(new TriggerSagaStartEvent("id"));
            fail("Expected error");
        } catch (AssertionError e) {
            assertTrue("Got unexpected error: " + e.getMessage(), e.getMessage().contains("StubSaga.nonTransientResource"));
            assertTrue("Got unexpected error: " + e.getMessage(), e.getMessage().contains("transient"));
        }
    }

    @Test
    public void testFixtureApi_NonTransientResourceInjected_CheckDisabled() throws Exception {
        FixtureConfiguration fixture = new SagaTestFixture<>(StubSaga.class)
                .withTransienceCheckDisabled();
        fixture.registerResource(new NonTransientResource());
        fixture.givenNoPriorActivity()
                .whenAggregate("id").publishes(new TriggerSagaStartEvent("id"));
    }

    @Test // testing issue AXON-279
    public void testFixtureApi_PublishedEvent_NoHistoricActivity() throws Exception {
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.givenNoPriorActivity()
                .whenPublishingA(new GenericEventMessage<>(new TriggerSagaStartEvent("id")))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", "id");
    }

    @Test
    public void testFixtureApi_WithApplicationEvents() throws Exception {
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
                .expectDispatchedCommands()
                .expectPublishedEvents();
    }

    @Test
    public void testFixtureApi_WhenEventIsPublishedToEventBus() throws Exception {
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
    }

    @Test
    public void testFixtureApi_ElapsedTimeBetweenEventsHasEffectOnScheduler() throws Exception {
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
    }


    @Test
    public void testFixtureApi_WhenTimeElapses_UsingMockGateway() throws Exception {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        final StubGateway gateway = mock(StubGateway.class);
        fixture.registerCommandGateway(StubGateway.class, gateway);
        when(gateway.send(eq("Say hi!"))).thenReturn("Hi again!");

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!", "Hi again!")
                .expectPublishedEventsMatching(noEvents());

        verify(gateway).send("Say hi!");
        verify(gateway).send("Hi again!");
    }

    @Test
    public void testSchedulingEventsAsMessage() throws Exception {
        UUID identifier = UUID.randomUUID();
        SagaTestFixture fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenNoPriorActivity()
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                .whenPublishingA(GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(identifier.toString())))
                .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent.class);
    }

    @Test
    public void testSchedulingEventsAsDomainEventMessage() throws Exception {
        UUID identifier = UUID.randomUUID();
        SagaTestFixture fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenNoPriorActivity()
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                .whenAggregate(UUID.randomUUID().toString()).publishes(GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(identifier.toString())))
                .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent.class);
    }

    @Test
    public void testScheduledEventsInPastAsDomainEventMessage() throws Exception {
        UUID identifier = UUID.randomUUID();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAggregate(UUID.randomUUID().toString()).published(GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(identifier.toString())))
                .whenTimeElapses(Duration.ofMinutes(1))
                .expectScheduledEventOfType(Duration.ofMinutes(9), TimerTriggeredEvent.class);
    }

    @Test
    public void testScheduledEventsInPastAsEventMessage() throws Exception {
        UUID identifier = UUID.randomUUID();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAPublished(GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(identifier.toString())))
                .whenTimeElapses(Duration.ofMinutes(1))
                .expectScheduledEventOfType(Duration.ofMinutes(9), TimerTriggeredEvent.class);
    }

    @Test
    public void testFixtureApi_WhenTimeElapses_UsingDefaults() throws Exception {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                // since we return null for the command, the other is never sent...
                .expectDispatchedCommands("Say hi!")
                .expectPublishedEventsMatching(noEvents());
    }

    @Test
    public void testFixtureApi_WhenTimeElapses_UsingCallbackBehavior() throws Exception {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        CallbackBehavior commandHandler = mock(CallbackBehavior.class);
        when(commandHandler.handle(eq("Say hi!"), isA(MetaData.class))).thenReturn("Hi again!");
        fixture.setCallbackBehavior(commandHandler);
        fixture.registerCommandGateway(StubGateway.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!", "Hi again!")
                .expectPublishedEventsMatching(noEvents());

        verify(commandHandler, times(2)).handle(isA(Object.class), eq(MetaData.emptyInstance()));
    }

    @Test
    public void testFixtureApi_WhenTimeAdvances() throws Exception {
        String identifier = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        SagaTestFixture<StubSaga> fixture = new SagaTestFixture<>(StubSaga.class);
        fixture.registerCommandGateway(StubGateway.class);
        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2))

                .whenTimeAdvancesTo(Instant.now().plus(Duration.ofDays(1)))

                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!");
    }
}
