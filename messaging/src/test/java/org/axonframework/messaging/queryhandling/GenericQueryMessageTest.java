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

package org.axonframework.messaging.queryhandling;

import org.assertj.core.api.Assertions;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link GenericQueryMessage}.
 *
 * @author Steven van Beelen
 */
class GenericQueryMessageTest extends MessageTestSuite<GenericQueryMessage> {

    @Override
    protected GenericQueryMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericQueryMessage(delegate);
    }

    @Override
    protected <P> GenericQueryMessage buildMessage(@Nullable P payload) {
        return new GenericQueryMessage(new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                       payload), 42);
    }

    @Override
    protected void validateDefaultMessage(@NonNull GenericQueryMessage result) {
        assertThat(result.priority()).isNotPresent();
    }

    @Override
    protected void validateMessageSpecifics(@NonNull GenericQueryMessage actual, @NonNull GenericQueryMessage result) {
        assertThat(result.priority()).hasValue(42);
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
            GenericQueryMessage exMessage = buildMessage(exStringPayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(String.class);

            assertThat(acPayload).isEqualTo(exStringPayload);
        }

        @Test
        void payloadAsClassInvokesConverterOnDifferentType() {
            GenericQueryMessage exMessage = buildMessage(exBytePayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(String.class);

            assertThat(acPayload).isEqualTo(exStringPayload);
            verify(converter).convert(eq(exBytePayload), eq((Type) String.class));
        }

        @Test
        void payloadAsClassFailsWithConversionExceptionWithoutConverter() {
            GenericQueryMessage exMessage = buildMessage(exBytePayload);

            // test
            Assertions.assertThatThrownBy(() -> exMessage.payloadAs(Integer.class))
                      .isInstanceOf(ConversionException.class);
        }

        @Test
        void payloadAsTypeRefInvokesConverter() {
            GenericQueryMessage exMessage = buildMessage(exBytePayload)
                    .withConverter(converter);

            // test
            String acPayload = exMessage.payloadAs(new TypeReference<String>() {
            });

            assertThat(acPayload).isEqualTo(exStringPayload);
            verify(converter).convert(eq(exBytePayload), eq((Type) String.class));
        }

        @Test
        void payloadAsCachesConversion() {
            GenericQueryMessage exMessage = buildMessage(exBytePayload)
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
            GenericQueryMessage original = buildMessage(exBytePayload);

            // when
            GenericQueryMessage result = original.withConverter(converter);

            // then
            assertThat(result).isInstanceOf(GenericQueryMessage.class);
            assertThat(result).isNotSameAs(original);
        }

        @Test
        void withConverterPreservesMembers() {
            // given
            GenericQueryMessage original = buildMessage(exBytePayload);

            // when
            GenericQueryMessage result = original.withConverter(converter);

            // then
            assertThat(result).isInstanceOf(GenericQueryMessage.class);
            assertThat(result).isNotSameAs(original);
            assertThat(result.identifier()).isEqualTo(original.identifier());
            assertThat(result.type()).isEqualTo(original.type());
            assertThat(result.payload()).isEqualTo(original.payload());
            assertThat(result.payloadType()).isEqualTo(original.payloadType());
            assertThat(result.metadata()).isEqualTo(original.metadata());
            assertThat(result.priority()).isEqualTo(original.priority());
        }
    }
}