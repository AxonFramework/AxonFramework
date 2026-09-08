/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.fixture;

import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.SubscribableEventSource;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link AxonTestFixture.Customization#excludeWhenPhaseMessages()}, which drops the messages the test itself
 * published or dispatched in the when-phase from the {@link AxonTestPhase.Then} recordings.
 *
 * @author Mateusz Nowak
 */
class AxonTestFixtureWhenPhaseMessagesTest {

    private static final StudentNameChangedEvent WHEN_EVENT =
            new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
    private static final StudentNameChangedEvent HANDLER_EVENT =
            new StudentNameChangedEvent("my-studentId-2", "name-2", 2);

    @Nested
    class Events {

        @Test
        void withoutTheCustomizationTheWhenEventIsRecordedAlongsideTheHandlerOutput() {
            // given a handler republishing a second event
            var configurer = messagingConfigurer();
            publishOnFirstEvent(configurer, HANDLER_EVENT);

            var fixture = AxonTestFixture.with(configurer);

            // when / then both the test's own event and the handler's event are visible
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .events(WHEN_EVENT, HANDLER_EVENT);
        }

        @Test
        void withTheCustomizationOnlyTheHandlerOutputIsRecorded() {
            // given the same handler, on a fixture excluding when-phase messages
            var configurer = messagingConfigurer();
            publishOnFirstEvent(configurer, HANDLER_EVENT);

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .events(HANDLER_EVENT);
        }

        @Test
        void withTheCustomizationNoHandlerOutputLeavesNoEvents() {
            // given no event handler at all
            var configurer = messagingConfigurer();

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then the when-event alone does not count as published output
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .noEvents();
        }

        @Test
        void exclusionIsByMessageIdentifierSoAnEqualPayloadRepublishedByAHandlerSurvives() {
            // given a handler republishing a payload equal to the when-event
            var configurer = messagingConfigurer();
            publishOnFirstEvent(configurer, WHEN_EVENT);

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then the handler's copy remains, because exclusion matches on identifier and not on equality
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .events(WHEN_EVENT);
        }

        @Test
        void exclusionAppliesToTheCustomAssertionsToo() {
            // given
            var configurer = messagingConfigurer();
            publishOnFirstEvent(configurer, HANDLER_EVENT);

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .eventsSatisfy(events -> assertThat(events).extracting(EventMessage::payload)
                                                              .containsExactly(HANDLER_EVENT))
                   .eventsMatch(events -> events.size() == 1);
        }

        @Test
        void givenEventsRemainExcludedBecauseTheWhenPhaseResetsTheRecorders() {
            // given a fixture with a given-phase event, which the when-phase reset already removes
            var configurer = messagingConfigurer();

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then
            fixture.given()
                   .event(HANDLER_EVENT)
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .noEvents();
        }
    }

    @Nested
    class Commands {

        @Test
        void withoutTheCustomizationTheWhenCommandIsRecorded() {
            // given
            var configurer = messagingConfigurer();
            registerNoOpCommandHandler(configurer);

            var fixture = AxonTestFixture.with(configurer);

            // when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .commands(new ChangeStudentNameCommand("my-studentId-1", "name-1"));
        }

        @Test
        void withTheCustomizationTheWhenCommandIsNotRecorded() {
            // given
            var configurer = messagingConfigurer();
            registerNoOpCommandHandler(configurer);

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then the command the test dispatched itself is not handler output
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .noCommands();
        }

        @Test
        void withTheCustomizationACommandDispatchedByAHandlerIsStillRecorded() {
            // given an event handler dispatching a command
            var configurer = messagingConfigurer();
            registerNoOpCommandHandler(configurer);
            AtomicBoolean handled = new AtomicBoolean(false);
            configurer.componentRegistry(cr -> cr.registerDecorator(
                    SubscribableEventSource.class,
                    0,
                    (c, n, delegate) -> {
                        delegate.subscribe((events, context) -> {
                            if (!handled.getAndSet(true)) {
                                c.getComponent(CommandGateway.class)
                                 .sendAndWait(new ChangeStudentNameCommand("my-studentId-2", "name-2"));
                            }
                            return CompletableFuture.completedFuture(null);
                        });
                        return delegate;
                    }
            ));

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .event(WHEN_EVENT)
                   .then()
                   .commands(new ChangeStudentNameCommand("my-studentId-2", "name-2"))
                   .commandsSatisfy(commands -> assertThat(commands).hasSize(1));
        }

        @Test
        void theExclusionIsReportedWhenAnAssertionOnTheWhenCommandFails() {
            // given
            var configurer = messagingConfigurer();
            registerNoOpCommandHandler(configurer);

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then asserting on the excluded command fails rather than silently passing
            assertThatThrownBy(() -> fixture.given()
                                            .noPriorActivity()
                                            .when()
                                            .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                                            .then()
                                            .commands(new ChangeStudentNameCommand("my-studentId-1", "name-1")))
                    .isInstanceOf(AxonAssertionError.class);
        }
    }

    @Nested
    class GivenPhaseOnly {

        @Test
        void thenAfterGivenSeesTheGivenEventsRegardlessOfTheCustomization() {
            // given a fixture that skips the when-phase entirely
            var configurer = messagingConfigurer();

            var fixture = AxonTestFixture.with(configurer, c -> c.excludeWhenPhaseMessages());

            // when / then there is no when-phase, so nothing is excluded
            fixture.given()
                   .event(WHEN_EVENT)
                   .then()
                   .events(WHEN_EVENT);
        }
    }

    /**
     * Subscribes an event handler that publishes the given {@code payload} the first time it is invoked. Publishing
     * only once keeps the handler from reacting to its own output.
     */
    private static void publishOnFirstEvent(MessagingConfigurer configurer, StudentNameChangedEvent payload) {
        AtomicBoolean handled = new AtomicBoolean(false);
        configurer.componentRegistry(cr -> cr.registerDecorator(
                SubscribableEventSource.class,
                0,
                (c, n, delegate) -> {
                    delegate.subscribe((events, context) -> {
                        if (!handled.getAndSet(true)) {
                            return c.getComponent(EventSink.class)
                                    .publish(context, List.of(eventMessage(payload)));
                        }
                        return CompletableFuture.completedFuture(null);
                    });
                    return delegate;
                }
        ));
    }

    private static void registerNoOpCommandHandler(MessagingConfigurer configurer) {
        configurer.componentRegistry(cr -> cr.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(new QualifiedName(ChangeStudentNameCommand.class),
                                         (command, context) -> MessageStream.empty().cast())
        ));
    }

    private static EventMessage eventMessage(StudentNameChangedEvent payload) {
        return new GenericEventMessage(new MessageType(StudentNameChangedEvent.class),
                                       payload,
                                       Metadata.emptyInstance());
    }

    private static MessagingConfigurer messagingConfigurer() {
        return MessagingConfigurer.create()
                                  .componentRegistry(ComponentRegistry::disableEnhancerScanning);
    }
}
