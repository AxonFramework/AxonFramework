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
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test validating command handler result message assertions in {@link AxonTestFixture}
 * when using stateless annotated command handlers with a real Axon Server via testcontainers.
 * <p>
 * Specifically tests that {@code resultMessagePayload} and {@code resultMessagePayloadSatisfies}
 * correctly verify command return values, including automatic payload conversion.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Testcontainers
class AxonTestFixtureStatelessAnnotatedCommandHandlerIntegrationTest {

    @Container
    private static final AxonServerContainer axonServer = new AxonServerContainer()
            .withAxonServerHostname("localhost")
            .withDevMode(true);

    record TestCommand(boolean shouldSuccess) {
    }

    record TestCommandResult(String message) {
    }

    static class StatelessCommandHandler {

        @CommandHandler
        TestCommandResult handle(TestCommand cmd) {
            return cmd.shouldSuccess()
                    ? new TestCommandResult("success")
                    : new TestCommandResult("failure");
        }
    }

    private MessagingConfigurer configurerWithStatelessHandler() {
        return MessagingConfigurer.create()
                .componentRegistry(cr -> cr.registerComponent(
                        AxonServerConfiguration.class,
                        c -> AxonServerConfiguration.builder()
                                .componentName("statelessCommandHandlerIntegrationTest")
                                .servers(axonServer.getAxonServerAddress())
                                .build()
                ))
                .registerCommandHandlingModule(
                        CommandHandlingModule.named("StatelessCommandHandler")
                                .commandHandlers()
                                .autodetectedCommandHandlingComponent(c -> new StatelessCommandHandler())
                );
    }

    @Nested
    class WhenCommandShouldSucceed {

        @Test
        void thenResultMessagePayloadContainsSuccessMessage() {
            // given
            var configurer = configurerWithStatelessHandler();
            var fixture = AxonTestFixture.with(configurer);

            // when / then
            fixture.when()
                    .command(new TestCommand(true))
                    .then()
                    .success()
                    .resultMessagePayload(new TestCommandResult("success"));
        }
    }

    @Nested
    class WhenCommandShouldFail {

        @Test
        void thenResultMessagePayloadContainsFailureMessage() {
            // given
            var configurer = configurerWithStatelessHandler();
            var fixture = AxonTestFixture.with(configurer);

            // when / then
            fixture.when()
                    .command(new TestCommand(false))
                    .then()
                    .success()
                    .resultMessagePayload(new TestCommandResult("failure"));
        }
    }

    @Nested
    class WhenUsingTypedPayloadSatisfies {

        @Test
        void thenPayloadIsAutomaticallyConverted() {
            // given
            var configurer = configurerWithStatelessHandler();
            var fixture = AxonTestFixture.with(configurer);

            // when / then
            fixture.when()
                    .command(new TestCommand(true))
                    .then()
                    .success()
                    .resultMessagePayloadSatisfies(TestCommandResult.class, result -> {
                        assertThat(result.message()).isEqualTo("success");
                    });
        }
    }
}
