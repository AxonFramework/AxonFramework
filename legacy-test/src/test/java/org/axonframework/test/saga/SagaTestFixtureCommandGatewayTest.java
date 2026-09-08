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

package org.axonframework.test.saga;

import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link FixtureConfiguration#registerCommandGateway(Class)} and its stubbed form, which is how an Axon
 * Framework 4 saga test asserts what a Saga sent through a gateway interface of its own.
 *
 * @author Mateusz Nowak
 */
class SagaTestFixtureCommandGatewayTest {

    private final SagaTestFixture<GatewaySaga> fixture = new SagaTestFixture<>(GatewaySaga.class);

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Nested
    class WithoutAStub {

        @Test
        void whatTheSagaSendsThroughTheGatewayIsDispatched() {
            fixture.registerCommandGateway(StubGateway.class);

            fixture.givenNoPriorActivity()
                   .whenPublishingA(new OrderPlaced("order-1"))
                   .expectDispatchedCommands("say-hi-for-order-1");
        }

        /**
         * As in Axon Framework 4: with nothing answering the command, the result is not there yet and the gateway
         * hands back {@code null} rather than blocking.
         */
        @Test
        void aCallWithNoAnswerReturnsNull() {
            fixture.registerCommandGateway(StubGateway.class);

            fixture.givenNoPriorActivity()
                   .whenPublishingA(new OrderPlaced("order-1"))
                   .expectAssociationWith("reply", "none");
        }

        @Test
        void theGatewayIsAvailableAsAHandlerParameter() {
            StubGateway gateway = fixture.registerCommandGateway(StubGateway.class);

            assertThat(gateway).isNotNull();
            fixture.givenNoPriorActivity()
                   .whenPublishingA(new OrderPlaced("order-1"))
                   .expectActiveSagas(1);
        }
    }

    @Nested
    class WithAStub {

        @Test
        void theStubDecidesWhatTheCallReturns() {
            fixture.registerCommandGateway(StubGateway.class, command -> "hi back");

            fixture.givenNoPriorActivity()
                   .whenPublishingA(new OrderPlaced("order-1"))
                   .expectAssociationWith("reply", "hi back");
        }

        @Test
        void theCommandIsStillDispatchedWhenAStubAnswers() {
            fixture.registerCommandGateway(StubGateway.class, command -> "hi back");

            fixture.givenNoPriorActivity()
                   .whenPublishingA(new OrderPlaced("order-1"))
                   .expectDispatchedCommands("say-hi-for-order-1");
        }

        @Test
        void aFailingStubSurfacesItsOwnException() {
            fixture.registerCommandGateway(StubGateway.class, command -> {
                throw new IllegalStateException("the stub said no");
            });

            assertThatThrownBy(() -> fixture.givenNoPriorActivity()
                                            .whenPublishingA(new OrderPlaced("order-1"))
                                            .expectSuccessfulHandlerExecution())
                    .isInstanceOf(AxonAssertionError.class)
                    .hasMessageContaining("the stub said no");
        }
    }

    @Nested
    class ProxyBehaviour {

        @Test
        void objectMethodsAreAnsweredWithoutDispatching() {
            StubGateway gateway = fixture.registerCommandGateway(StubGateway.class);

            assertThat(gateway).isEqualTo(gateway)
                               .isNotEqualTo(new Object())
                               .hasToString("StubCommandGateway[" + StubGateway.class.getName() + "]");
            assertThat(gateway.hashCode()).isEqualTo(System.identityHashCode(gateway));
        }

        @Test
        void aFutureReturningMethodHandsBackTheDispatchResult() {
            AsyncGateway gateway = fixture.registerCommandGateway(AsyncGateway.class);
            fixture.givenNoPriorActivity();

            CompletableFuture<Object> result = gateway.sendAsync("a-command");

            assertThat(result).isNotNull();
        }

        @Test
        void aMethodWithoutArgumentsIsReported() {
            NoArgumentGateway gateway = fixture.registerCommandGateway(NoArgumentGateway.class);
            fixture.givenNoPriorActivity();

            assertThatThrownBy(gateway::send)
                    .isInstanceOf(FixtureExecutionException.class)
                    .hasMessageContaining("at least one parameter");
        }

        @Test
        void aClassRatherThanAnInterfaceIsReported() {
            assertThatThrownBy(() -> fixture.registerCommandGateway(String.class))
                    .isInstanceOf(FixtureExecutionException.class)
                    .hasMessageContaining("not an interface");
        }
    }

    public record OrderPlaced(String orderId) {

    }

    /**
     * The gateway interface Axon Framework 4's own saga test used.
     */
    public interface StubGateway {

        String send(String command);
    }

    public interface AsyncGateway {

        CompletableFuture<Object> sendAsync(String command);
    }

    public interface NoArgumentGateway {

        String send();
    }

    @SuppressWarnings({"unused", "removal"})
    public static class GatewaySaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "orderId")
        public void on(OrderPlaced event, SagaLifecycle lifecycle, StubGateway gateway) {
            String reply = gateway.send("say-hi-for-" + event.orderId());
            lifecycle.associateWith("reply", reply == null ? "none" : reply);
        }
    }
}
