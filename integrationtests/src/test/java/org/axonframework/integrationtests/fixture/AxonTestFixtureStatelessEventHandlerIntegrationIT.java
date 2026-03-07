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

package org.axonframework.integrationtests.fixture;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Integration test validating stateless event handler assertions in {@link AxonTestFixture}
 * when using a real Axon Server via testcontainers.
 * <p>
 * Mirrors the scenarios from the unit-level {@code AxonTestFixtureStatelessEventHandlerTest},
 * verifying that events processed by a pooled streaming event processor correctly trigger
 * commands through the {@link CommandGateway} when backed by Axon Server infrastructure.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
class AxonTestFixtureStatelessEventHandlerIntegrationIT {

    private static final Logger logger = LoggerFactory.getLogger(AxonTestFixtureStatelessEventHandlerIntegrationIT.class);

    private static final AxonServerContainer container = new AxonServerContainer(
            "docker.axoniq.io/axoniq/axonserver:2025.2.0")
            .withAxonServerHostname("localhost")
            .withDevMode(true)
            .withReuse(true);

    record StudentNameChangedEvent(String id, String name, Integer change) {
    }

    record SendNotificationCommand(String recipientId, String message) {
    }

    private AxonTestFixture fixture;

    @BeforeAll
    static void beforeAll() {
        container.start();
        logger.info("Using Axon Server for integration test. UI is available at http://localhost:{}",
                     container.getHttpPort());
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }

    @BeforeEach
    void setUp() {
        purgeEvents();
        fixture = AxonTestFixture.with(whenEventThenCommandConfig());
    }

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Nested
    class GivenEventsThenAwaitCommands {

        @Test
        void thenAwaitedCommandIsDispatched() {
            // given
            var studentNameChanged = new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
            var expectedCommand = new SendNotificationCommand("my-studentId-1", "Name changed");

            // when / then
            fixture.given()
                   .events(studentNameChanged)
                   .then()
                   .await(r -> r.commands(expectedCommand), Duration.ofSeconds(5));
        }
    }

    @Nested
    class GivenEventsWhenNothingThenAwaitCommands {

        @Test
        void thenAwaitedCommandIsDispatched() {
            // given
            var studentNameChanged = new StudentNameChangedEvent("my-studentId-1", "name-1", 1);
            var expectedCommand = new SendNotificationCommand("my-studentId-1", "Name changed");

            // when / then
            fixture.given()
                   .events(studentNameChanged)
                   .when()
                   .nothing()
                   .then()
                   .await(r -> r.commands(expectedCommand), Duration.ofSeconds(5));
        }
    }

    private EventSourcingConfigurer whenEventThenCommandConfig() {
        var configurer = EventSourcingConfigurer.create();

        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(container.getHost() + ":" + container.getGrpcPort());
        configurer.componentRegistry(cr -> cr.registerComponent(
                AxonServerConfiguration.class,
                c -> axonServerConfiguration
        ));

        configurer.messaging(cr -> cr.eventProcessing(ep -> ep.pooledStreaming(ps -> ps.processor(
                EventProcessorModule
                        .pooledStreaming("test-given-event-then-command")
                        .eventHandlingComponents(c -> c.declarative(
                                cfg -> SimpleEventHandlingComponent.create("test").subscribe(
                                        new QualifiedName(StudentNameChangedEvent.class),
                                        AxonTestFixtureStatelessEventHandlerIntegrationIT::handleStudentNameChanged
                                )
                        )).notCustomized()
        ))));

        return configurer;
    }

    private static MessageStream.Empty<Message> handleStudentNameChanged(EventMessage e, ProcessingContext ctx) {
        var commandDispatcher = ctx.component(CommandGateway.class);
        var converter = ctx.component(Converter.class);
        var payload = e.payloadAs(StudentNameChangedEvent.class, converter);
        commandDispatcher.send(new SendNotificationCommand(
                payload.id(),
                "Name changed"
        ), ctx);
        return MessageStream.empty();
    }

    private void purgeEvents() {
        try {
            AxonServerContainerUtils.purgeEventsFromAxonServer(
                    container.getHost(),
                    container.getHttpPort(),
                    "default",
                    AxonServerContainerUtils.DCB_CONTEXT
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to purge event storage", e);
        }
    }
}
