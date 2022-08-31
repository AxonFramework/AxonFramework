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

package org.axonframework.modelling.saga.metamodel;

import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

class AnnotationSagaMetaModelFactoryTest {

    private AnnotationSagaMetaModelFactory testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationSagaMetaModelFactory();
    }

    @Test
    void inspectSaga() {
        SagaModel<MySaga> sagaModel = testSubject.modelOf(MySaga.class);

        Optional<AssociationValue> actual = sagaModel.resolveAssociation(asEventMessage(new MySagaStartEvent("value")));
        assertTrue(actual.isPresent());
        assertEquals("value", actual.get().getValue());
        assertEquals("property", actual.get().getKey());
    }

    public static class MySaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "property")
        public void handle(MySagaStartEvent event) {

        }

        @SagaEventHandler(associationProperty = "property")
        public void handle(MySagaUpdateEvent event) {

        }

        @SagaEventHandler(associationProperty = "property")
        public void handle(MySagaEndEvent event) {

        }
    }

    public abstract static class MySagaEvent {

        private final String property;

        public MySagaEvent(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }
    }

    private static class MySagaStartEvent extends MySagaEvent {
        public MySagaStartEvent(String property) {
            super(property);
        }
    }

    private static class MySagaUpdateEvent extends MySagaEvent {
        public MySagaUpdateEvent(String property) {
            super(property);
        }
    }

    private static class MySagaEndEvent extends MySagaEvent {
        public MySagaEndEvent(String property) {
            super(property);
        }
    }
}
