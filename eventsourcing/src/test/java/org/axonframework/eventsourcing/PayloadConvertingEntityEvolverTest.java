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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PayloadConvertingEntityEvolver}.
 *
 * @author Mitchell Herrijgers
 */
class PayloadConvertingEntityEvolverTest {

    private static final String ENTITY = "base";
    private static final String PAYLOAD = ":test-event";

    private PayloadConvertingEntityEvolver<String, String> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new PayloadConvertingEntityEvolver<>(String.class, (entity, event) -> entity + event);
    }

    @Test
    void appliesEventWhenTypeMatchesAndPayloadTypeIsConvertible() {
        String result = testSubject.evolve(ENTITY,
                                           new GenericEventMessage<>(new MessageType(String.class), PAYLOAD),
                                           ProcessingContext.NONE);

        assertEquals(ENTITY + PAYLOAD, result);
    }

    @Test
    void throwsExceptionWhenTypeMatchesAndPayloadTypeCannotBeConverted() {
        assertThrows(ClassCastException.class,
                     () -> testSubject.evolve(ENTITY,
                                              new GenericEventMessage<>(new MessageType(String.class),
                                                                        new StringBuilder()),
                                              ProcessingContext.NONE));
    }

    @Test
    void invokesEvolveRegardlessOfQualifiedNameMismatch() {
        String result = testSubject.evolve(ENTITY,
                                           new GenericEventMessage<>(new MessageType(Integer.class), PAYLOAD),
                                           ProcessingContext.NONE);
        assertEquals(ENTITY + PAYLOAD, result);
    }

    @Test
    void throwsExceptionIfNullPayloadTypeIsSupplied() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingEntityEvolver<String, String>(
                null,
                (entity, event) -> entity + event
        ));
    }

    @Test
    void throwsExceptionIfNullEventStateApplierIsSupplied() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingEntityEvolver<String, String>(
                String.class,
                null
        ));
    }
}