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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SingleEventTypeStateApplierTest {

    SingleEventTypeStateApplier<String, String> applier = new SingleEventTypeStateApplier<>(
            new QualifiedName(String.class),
            String.class,
            (model, event) -> model + event
    );

    @Test
    void appliesEventWhenTypeMatchesAndPayloadTypeIsConvertible() {
        String result = applier.apply("base", new GenericEventMessage<>(
                new MessageType(String.class),
                ":test-event"
        ), ProcessingContext.NONE);
        assertEquals("base:test-event", result);
    }

    @Test
    void throwsExceptionWhenTypeMatchesAndPayloadTypeCannotBeConverted() {
        assertThrows(ClassCastException.class, () -> applier.apply("base", new GenericEventMessage<>(
                new MessageType(String.class),
                new StringBuilder()
        ), ProcessingContext.NONE));
    }

    @Test
    void doesNothingIfQualifiedNameDoesNotMatch() {
        String result = applier.apply("base", new GenericEventMessage<>(
                new MessageType(Integer.class),
                25
        ), ProcessingContext.NONE);
        assertEquals("base", result);
    }

    @Test
    void throwsExceptionIfNullQualifiedNameIsSupplied() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new SingleEventTypeStateApplier<>(
                null,
                String.class,
                (model, event) -> model + event
        ));
    }

    @Test
    void throwsExceptionIfNullPayloadTypeIsSupplied() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new SingleEventTypeStateApplier<String, String>(
                new QualifiedName(String.class),
                null,
                (model, event) -> model + event
        ));
    }

    @Test
    void throwsExceptionIfNullEventStateApplierIsSupplied() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new SingleEventTypeStateApplier<String, String>(
                new QualifiedName(String.class),
                String.class,
                null
        ));
    }
}