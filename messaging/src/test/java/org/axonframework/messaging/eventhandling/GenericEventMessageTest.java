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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericEventMessage}.
 *
 * @author Allard Buijze
 */
class GenericEventMessageTest extends MessageTestSuite<EventMessage> {

    private static final Instant TEST_TIMESTAMP = Instant.now();

    @Override
    protected EventMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericEventMessage(delegate, TEST_TIMESTAMP);
    }

    @Override
    protected <P> EventMessage buildMessage(@Nullable P payload) {
        return new GenericEventMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Override
    protected void validateDefaultMessage(@Nonnull EventMessage result) {
        assertThat(TEST_TIMESTAMP).isEqualTo(result.timestamp());
    }

    @Override
    protected void validateMessageSpecifics(@Nonnull EventMessage actual, @Nonnull EventMessage result) {
        assertThat(actual.timestamp()).isEqualTo(result.timestamp());
    }

    @Test
    void toStringIsAsExpected() {
        String actual = EventTestUtils.asEventMessage("MyPayload")
                                      .andMetadata(Metadata.with("key", "value").and("key2", "13"))
                                      .toString();
        assertTrue(actual.startsWith(
                           "GenericEventMessage{type={java.lang.String#0.0.1}, payload={MyPayload}, metadata={"
                   ),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.contains("', timestamp='"), "Wrong output: " + actual);
        assertTrue(actual.endsWith("}"), "Wrong output: " + actual);
    }
}
