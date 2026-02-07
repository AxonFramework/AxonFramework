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

package org.axonframework.messaging.commandhandling.sequencing;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link RoutingKeyCommandSequencingPolicy}
 *
 * @author Jakob Hatzl
 */
class RoutingKeyCommandSequencingPolicyTest {

    @Test
    void sameRoutingKeySameIdentifier() {
        final String exPayload1 = "payload1";
        final String exPayload2 = "payload2";
        final String exRoutingKey = "exRoutingKey";
        final RoutingKeyCommandSequencingPolicy testSubject = new RoutingKeyCommandSequencingPolicy();

        Object acIdentifier1 = testSubject.getSequenceIdentifierFor(asCommandMessage(exPayload1, exRoutingKey),
                                                                    new StubProcessingContext()).orElseThrow();
        Object acIdentifier2 = testSubject.getSequenceIdentifierFor(asCommandMessage(exPayload2, exRoutingKey),
                                                                    new StubProcessingContext()).orElseThrow();
        assertEquals(exRoutingKey, acIdentifier1);
        assertEquals(exRoutingKey, acIdentifier2);
    }

    @Test
    void differentRoutingKeyDifferentIdentifier() {
        final String exPayload1 = "payload1";
        final String exPayload2 = "payload2";
        final String exRoutingKey1 = "exRoutingKey1";
        final String exRoutingKey2 = "exRoutingKey2";
        final RoutingKeyCommandSequencingPolicy testSubject = new RoutingKeyCommandSequencingPolicy();

        Object acIdentifier1 = testSubject.getSequenceIdentifierFor(asCommandMessage(exPayload1, exRoutingKey1),
                                                                    new StubProcessingContext()).orElseThrow();
        Object acIdentifier2 = testSubject.getSequenceIdentifierFor(asCommandMessage(exPayload2, exRoutingKey2),
                                                                    new StubProcessingContext()).orElseThrow();
        assertEquals(exRoutingKey1, acIdentifier1);
        assertEquals(exRoutingKey2, acIdentifier2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void handlesNullAndEmptyAsMissingRoutingKey(String exRoutingKey) {
        final String exPayload = "payload1";
        final RoutingKeyCommandSequencingPolicy testSubject = new RoutingKeyCommandSequencingPolicy();

        Optional<Object> acIdentifier1 = testSubject.getSequenceIdentifierFor(asCommandMessage(exPayload, exRoutingKey),
                                                                              new StubProcessingContext());
        assertTrue(acIdentifier1.isEmpty());
    }

    private static CommandMessage asCommandMessage(String payload, String routingKey) {
        return new GenericCommandMessage(MessageType.fromString("commandmessage#1.0"), payload,
                                         Map.of(), routingKey, null);
    }
}