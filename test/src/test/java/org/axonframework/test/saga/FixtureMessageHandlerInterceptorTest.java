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

package org.axonframework.test.saga;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.util.Objects;

/**
 * Test class validating the registration of {@link MessageHandlerInterceptor} instances through the
 * {@link FixtureConfiguration}.
 *
 * @author Steven van Beelen
 */
class FixtureMessageHandlerInterceptorTest {

    private static final String META_DATA_KEY = "key";

    private FixtureConfiguration fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(TestSaga.class);
    }

    @Test
    void registeredEventHandlerInterceptorIsInvokedBeforeHandlingEvents() {
        String testId = "some-identifier";
        String testValue = "some-value";

        fixture.registerEventHandlerInterceptor(new CustomEventHandlerInterceptor(testValue))
               .givenNoPriorActivity()
               .whenPublishingA(new SagaStartEvent(testId))
               .expectDispatchedCommands(new StartProcessCommand(testId, testValue));
    }

    private static class CustomEventHandlerInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

        private final String value;

        private CustomEventHandlerInterceptor(String value) {
            this.value = value;
        }

        @Override
        public Object handle(@NotNull UnitOfWork<? extends EventMessage<?>> unitOfWork,
                             @NotNull InterceptorChain interceptorChain) throws Exception {
            unitOfWork.transformMessage(event -> event.withMetaData(MetaData.with(META_DATA_KEY, value)));
            return interceptorChain.proceedSync();
        }
    }

    private static class SagaStartEvent {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String identifier;

        private SagaStartEvent(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private static class StartProcessCommand {

        private final String identifier;
        private final String metaDataValue;

        private StartProcessCommand(String identifier, String metaDataValue) {
            this.identifier = identifier;
            this.metaDataValue = metaDataValue;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getMetaDataValue() {
            return metaDataValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StartProcessCommand that = (StartProcessCommand) o;
            return Objects.equals(identifier, that.identifier)
                    && Objects.equals(metaDataValue, that.metaDataValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, metaDataValue);
        }

        @Override
        public String toString() {
            return "StartProcessCommand{" +
                    "identifier='" + identifier + '\'' +
                    ", metaDataValue='" + metaDataValue + '\'' +
                    '}';
        }
    }

    @SuppressWarnings("unused")
    public static class TestSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "identifier")
        public void on(SagaStartEvent event,
                       @MetaDataValue(META_DATA_KEY) String value,
                       CommandGateway commandGateway) {
            commandGateway.send(new StartProcessCommand(event.getIdentifier(), value), ProcessingContext.NONE);
        }
    }
}
