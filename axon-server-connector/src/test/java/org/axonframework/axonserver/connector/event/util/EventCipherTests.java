/*
 * Copyright (c) 2018-2022. Axon Framework
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

package org.axonframework.axonserver.connector.event.util;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class EventCipherTests {

    /**
     * Most basic test of a simple encryption scenario.
     */
    @Test
    void smoke() {
        String message = "Hello World! AxonIQ Rulez!";
        String aggregateIdentifier = "1234";
        byte[] clearPayload = message.getBytes(StandardCharsets.UTF_8);
        EventCipher eventCipher = new EventCipher(getRandomKeyBytes(16));
        Event clearEvent = Event
                .newBuilder()
                .setAggregateIdentifier(aggregateIdentifier)
                .setPayload(SerializedObject
                        .newBuilder()
                        .setData(ByteString.copyFrom(clearPayload))
                        .build())
                .build();

        Event cryptoEvent = eventCipher.encrypt(clearEvent);
        byte[] encryptedPayload = cryptoEvent.getPayload().getData().toByteArray();

        assertTrue(!Arrays.equals(encryptedPayload, clearPayload));
        assertTrue(encryptedPayload.length % 16 == 0);
        assertEquals(aggregateIdentifier, cryptoEvent.getAggregateIdentifier());

        Event decipheredEvent = eventCipher.decrypt(cryptoEvent);
        assertEquals(clearEvent, decipheredEvent);
    }

    @Test
    void defaultEventCipherShouldNotEncrypt() {
        String message = "Hello World! AxonIQ Rulez!";
        String aggregateIdentifier = "1234";
        byte[] clearPayload = message.getBytes(StandardCharsets.UTF_8);
        EventCipher eventCipher = new EventCipher();
        Event clearEvent = Event
                .newBuilder()
                .setAggregateIdentifier(aggregateIdentifier)
                .setPayload(SerializedObject
                        .newBuilder()
                        .setData(ByteString.copyFrom(clearPayload))
                        .build())
                .build();

        Event cryptoEvent = eventCipher.encrypt(clearEvent);
        assertEquals(clearEvent, cryptoEvent);

        Event decipheredEvent = eventCipher.decrypt(cryptoEvent);
        assertEquals(clearEvent, decipheredEvent);
    }

    @Test
    void encryptionShouldBeRandom() {
        String message = "Hello World! AxonIQ Rulez!";
        String aggregateIdentifier = "1234";
        byte[] clearPayload = message.getBytes(StandardCharsets.UTF_8);
        EventCipher eventCipher = new EventCipher(getRandomKeyBytes(16));
        Event clearEvent = Event
                .newBuilder()
                .setAggregateIdentifier(aggregateIdentifier)
                .setPayload(SerializedObject
                        .newBuilder()
                        .setData(ByteString.copyFrom(clearPayload))
                        .build())
                .build();

        Event cryptoEvent1 = eventCipher.encrypt(clearEvent);
        Event cryptoEvent2 = eventCipher.encrypt(clearEvent);
        assertNotEquals(cryptoEvent1, cryptoEvent2);
    }

    private String getRandomKeyString(int length) {
        byte[] bytes = new byte[length];
        for(int i = 0; i < length; i++) {
            bytes[i] = (byte)(ThreadLocalRandom.current().nextInt(33, 127));
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    };

    private byte[] getRandomKeyBytes(int length) {
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    };
}
