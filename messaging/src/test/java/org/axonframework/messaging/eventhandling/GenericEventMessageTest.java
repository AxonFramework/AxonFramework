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

package org.axonframework.messaging.eventhandling;

import org.assertj.core.api.Assertions;
import org.axonframework.common.TypeReference;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.GenericResultMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.ConversionException;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link GenericEventMessage}.
 *
 * @author Allard Buijze
 */
class GenericEventMessageTest extends MessageTestSuite<GenericEventMessage> {

    private static final Instant TEST_TIMESTAMP = Instant.now();

    @Override
    protected GenericEventMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericEventMessage(delegate, TEST_TIMESTAMP);
    }

    @Override
    protected <P> GenericEventMessage buildMessage(@Nullable P payload) {
        return new GenericEventMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Override
    protected void validateDefaultMessage(@NonNull GenericEventMessage result) {
        assertThat(TEST_TIMESTAMP).isEqualTo(result.timestamp());
    }

    @Override
    protected void validateMessageSpecifics(@NonNull GenericEventMessage actual, @NonNull GenericEventMessage result) {
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

    @Nested
    class WithConverter {

        private Converter converter;
        private String exStringPayload = "payload";
        private byte[] exBytePayload = exStringPayload.getBytes(StandardCharsets.UTF_8);

        @BeforeEach
        void setUp() {
            converter = spy(new ChainingContentTypeConverter());
        }

        @AfterEach
        void tearDown() {
            verifyNoMoreInteractions(converter);
        }

        @Test
        void payloadAsClassReturnsPayloadWithoutConversionOnSameType() {
            GenericEventMessage exMessage = buildMessage(exStringPayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(String.class);

            assertThat(acPayload).isEqualTo(exStringPayload);
        }

        @Test
        void payloadAsClassInvokesConverterOnDifferentType() {
            GenericEventMessage exMessage = buildMessage(exBytePayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(String.class);

            assertThat(acPayload).isEqualTo(exStringPayload);
            verify(converter).convert(eq(exBytePayload), eq((Type) String.class));
        }

        @Test
        void payloadAsClassFailsWithConversionExceptionWithoutConverter() {
            GenericEventMessage exMessage = buildMessage(exBytePayload);

            // test
            Assertions.assertThatThrownBy(() -> exMessage.payloadAs(Integer.class))
                      .isInstanceOf(ConversionException.class);
        }

        @Test
        void payloadAsTypeRefInvokesConverter() {
            GenericEventMessage exMessage = buildMessage(exBytePayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(new TypeReference<String>() {
            });

            assertThat(acPayload).isEqualTo(exStringPayload);
            verify(converter).convert(eq(exBytePayload), eq((Type) String.class));
        }

        @Test
        void payloadAsCachesConversion() {
            GenericEventMessage exMessage = buildMessage(exBytePayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(String.class);
            String acPayload2 = exMessage.payloadAs(String.class);

            assertThat(acPayload).isEqualTo(exStringPayload);
            assertThat(acPayload2).isEqualTo(exStringPayload);
            verify(converter, times(1)).convert(eq(exBytePayload), eq((Type) String.class));
        }

        @Test
        void withConverterReturnsNewInstanceOfSameConcreteType() {
            // given
            GenericEventMessage original = buildMessage(exBytePayload);

            // when
            GenericEventMessage result = original.withConverter(converter);

            // then
            assertThat(result).isInstanceOf(GenericEventMessage.class);
            assertThat(result).isNotSameAs(original);
        }

        @Test
        void withConverterPreservesMembers() {
            // given
            GenericEventMessage original = buildMessage(exBytePayload);

            // when
            GenericEventMessage result = original.withConverter(converter);

            // then
            assertThat(result).isInstanceOf(GenericEventMessage.class);
            assertThat(result).isNotSameAs(original);
            assertThat(result.identifier()).isEqualTo(original.identifier());
            assertThat(result.type()).isEqualTo(original.type());
            assertThat(result.payload()).isEqualTo(original.payload());
            assertThat(result.payloadType()).isEqualTo(original.payloadType());
            assertThat(result.metadata()).isEqualTo(original.metadata());
            assertThat(result.timestamp()).isEqualTo(original.timestamp());
        }
    }
}
