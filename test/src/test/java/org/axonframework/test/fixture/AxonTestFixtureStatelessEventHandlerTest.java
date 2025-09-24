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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.configuration.DefaultEventHandlingComponentBuilder;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.test.fixture.sampledomain.SendNotificationCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.time.Duration;

public class AxonTestFixtureStatelessEventHandlerTest {

    @Test
    void givenEventsThenCommand_Failure() {
        var configurer = whenEventThenCommandConfig();

        var fixture = AxonTestFixture.with(configurer);

        var studentNameChanged = new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
        var expectedCommand = new SendNotificationCommand("my-studentId-1", "Name changed");
        fixture.given()
               .events(studentNameChanged)
               .then()
               .commands(expectedCommand);
    }

    @Test
    void givenEventsThenCommand_Success_AwaitCommandsDefault() {
        var configurer = whenEventThenCommandConfig();

        var fixture = AxonTestFixture.with(configurer);

        var studentNameChanged = new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
        var expectedCommand = new SendNotificationCommand("my-studentId-1", "Name changed");
        fixture.given()
               .events(studentNameChanged)
               .then()
               .awaitCommands(expectedCommand);
    }

    @Test
    void givenEventsThenCommand_Success_AwaitCommandsDefaultMillis() {
        var configurer = whenEventThenCommandConfig();

        var fixture = AxonTestFixture.with(configurer);

        var studentNameChanged = new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
        var expectedCommand = new SendNotificationCommand("my-studentId-1", "Name changed");
        fixture.given()
               .events(studentNameChanged)
               .then()
               .awaitCommands(Duration.ofMillis(100), expectedCommand);
    }

    @Nonnull
    private static EventSourcingConfigurer whenEventThenCommandConfig() {
        var configurer = EventSourcingConfigurer.create();
        configurer.messaging(cr -> cr.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(
                EventProcessorModule
                        .pooledStreaming("test-given-event-then-command")
                        .eventHandlingComponents(c -> c.declarative(
                                cfg -> new DefaultEventHandlingComponentBuilder(new SimpleEventHandlingComponent())
                                        .handles(new QualifiedName(StudentNameChangedEvent.class),
                                                 (e, ctx) -> {
                                                     var commandDispatcher = ctx.component(CommandGateway.class);
                                                     var payload = e.payloadAs(StudentNameChangedEvent.class);
                                                     commandDispatcher.send(new SendNotificationCommand(
                                                             payload.id(),
                                                             "Name changed"
                                                     ), ctx);
                                                     return MessageStream.empty();
                                                 }).build()
                        )).notCustomized()
        ))));
        return configurer;
    }
}
