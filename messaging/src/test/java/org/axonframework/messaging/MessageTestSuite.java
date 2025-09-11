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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.TypeReference;
import org.axonframework.serialization.ChainingContentTypeConverter;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test suite to validate {@link Message} implementation.
 *
 * @param <M> The {@link Message} implementation under test.
 * @author Steven van Beelen
 */
public abstract class MessageTestSuite<M extends Message> {

    protected static final String TEST_IDENTIFIER = "testIdentifier";
    protected static final MessageType TEST_TYPE = new MessageType("message");
    protected static final String TEST_PAYLOAD = "payload";
    protected static final Class<String> TEST_PAYLOAD_TYPE = String.class;
    protected static final Map<String, String> TEST_METADATA = Map.of("key", "value");

    private static final String STRING_PAYLOAD = "some-string-payload";
    private static final ChainingContentTypeConverter CONVERTER = new ChainingContentTypeConverter();
    private static final TypeReference<byte[]> BYTE_ARRAY_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<String> STRING_TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * Builds a default {@link Message} used by this test suite.
     *
     * @return A default {@link Message} used by this test suite.
     */
    protected abstract M buildDefaultMessage();

    /**
     * Builds a {@link Message} used by this test suite.
     *
     * @param <P> The payload type for the {@link Message} implementation under test.
     * @return A {@link Message} used by this test suite.
     */
    protected abstract <P> M buildMessage(@Nullable P payload);

    /**
     * Overridable method to validate {@link #buildDefaultMessage() default} {@link Message} implementation specific
     * details during assertion.
     *
     * @param result The resulting {@link Message} implementation to validate against.
     */
    protected void validateDefaultMessage(@Nonnull M result) {
        // The default validations are done by the tests directly
    }

    /**
     * Overridable method to validate {@link Message} implementation specific details during assertion.
     *
     * @param actual The actual {@link Message} implementation.
     * @param result The resulting {@link Message} implementation to validate against.
     */
    protected void validateMessageSpecifics(@Nonnull M actual, @Nonnull M result) {
        // The default validations are done by the tests directly
    }

    @Test
    void containsDataAsExpected() {
        M testSubject = buildDefaultMessage();

        assertThat(TEST_IDENTIFIER).isEqualTo(testSubject.identifier());
        assertThat(TEST_TYPE).isEqualTo(testSubject.type());
        assertThat(TEST_PAYLOAD).isEqualTo(testSubject.payload());
        assertThat(TEST_PAYLOAD_TYPE).isEqualTo(testSubject.payloadType());
        assertThat(TEST_METADATA).isEqualTo(testSubject.metadata());
        validateDefaultMessage(testSubject);
    }

    @Test
    void payloadAsWithClassAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter spiedConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs(TEST_PAYLOAD_TYPE, spiedConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(spiedConverter);
    }

    @Test
    void payloadAsWithClassConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void secondPayloadAsWithClassInvocationReusesPreviousConversionResult() {
        String testPayload = STRING_PAYLOAD;
        Converter spiedConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs(byte[].class, spiedConverter);
        byte[] secondResult = testSubject.payloadAs(byte[].class, spiedConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(spiedConverter, times(1)).convert(any(), (Type) any());
    }

    @Test
    void secondPayloadAsWithClassInvocationAndNullResultReusesPreviousConversionResult() {
        Converter spiedConverter = spy(CONVERTER);
        when(spiedConverter.convert(any(), (Type) any())).thenReturn(null);

        M testSubject = buildMessage(STRING_PAYLOAD);

        byte[] firstResult = testSubject.payloadAs(byte[].class, spiedConverter);
        byte[] secondResult = testSubject.payloadAs(byte[].class, spiedConverter);

        assertThat(firstResult).isNull();
        assertThat(secondResult).isNull();
        verify(spiedConverter, times(1)).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithSameClassButDifferentConverterDoesConvertAgain() {
        String testPayload = STRING_PAYLOAD;
        Converter firstConverter = spy(CONVERTER);
        Converter secondConverter = spy(new ChainingContentTypeConverter());

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs(byte[].class, firstConverter);
        byte[] secondResult = testSubject.payloadAs(byte[].class, secondConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(firstConverter).convert(any(), (Type) any());
        verify(secondConverter).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithClassThrowsConversionExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(byte[].class, null))
                .isExactlyInstanceOf(ConversionException.class);
    }

    @Test
    void payloadAsWithTypeReferenceConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void secondPayloadAsWithTypeReferenceInvocationReusesPreviousConversionResult() {
        String testPayload = STRING_PAYLOAD;
        Converter spiedConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, spiedConverter);
        byte[] secondResult = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, spiedConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(spiedConverter, times(1)).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithTypeReferenceButDifferentConverterDoesConvertAgain() {
        String testPayload = STRING_PAYLOAD;
        Converter firstConverter = spy(CONVERTER);
        Converter secondConverter = spy(new ChainingContentTypeConverter());

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, firstConverter);
        byte[] secondResult = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, secondConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(firstConverter).convert(any(), (Type) any());
        verify(secondConverter).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithTypeReferenceThrowsConversionExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, null))
                .isExactlyInstanceOf(ConversionException.class);
    }

    @Test
    void payloadAsWithTypeInstanceOfClassAndAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter spiedConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs((Type) TEST_PAYLOAD_TYPE, spiedConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(spiedConverter);
    }

    @Test
    void payloadAsWithTypeConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs((Type) byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void secondPayloadAsWithTypeInvocationReusesPreviousConversionResult() {
        String testPayload = STRING_PAYLOAD;
        Converter spiedConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs((Type) byte[].class, spiedConverter);
        byte[] secondResult = testSubject.payloadAs((Type) byte[].class, spiedConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(spiedConverter, times(1)).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithTypeButDifferentConverterDoesConvertAgain() {
        String testPayload = STRING_PAYLOAD;
        Converter firstConverter = spy(CONVERTER);
        Converter secondConverter = spy(new ChainingContentTypeConverter());

        M testSubject = buildMessage(testPayload);

        byte[] firstResult = testSubject.payloadAs((Type) byte[].class, firstConverter);
        byte[] secondResult = testSubject.payloadAs((Type) byte[].class, secondConverter);

        assertThat(testPayload.getBytes()).isEqualTo(firstResult);
        assertThat(firstResult).isEqualTo(secondResult);
        verify(firstConverter).convert(any(), (Type) any());
        verify(secondConverter).convert(any(), (Type) any());
    }

    @Test
    void payloadAsWithTypeThrowsConversionExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs((Type) byte[].class, null))
                .isExactlyInstanceOf(ConversionException.class);
    }

    @Test
    void andMetadata() {
        Map<String, String> newMetadata = Map.of("k2", "v3");
        Metadata expectedMetadata = Metadata.from(TEST_METADATA).mergedWith(newMetadata);

        M testSubject = buildDefaultMessage();

        Message result = testSubject.andMetadata(newMetadata);

        assertThat(testSubject.payload()).isEqualTo(result.payload());
        assertThat(expectedMetadata).isEqualTo(result.metadata());
    }

    @Test
    void withMetadata() {
        Map<String, String> newMetadata = Map.of("k2", "v3");

        M testSubject = buildDefaultMessage();

        Message result = testSubject.withMetadata(newMetadata);

        assertThat(testSubject.payload()).isEqualTo(result.payload());
        assertThat(newMetadata).isEqualTo(result.metadata());
    }

    @Test
    void withConvertedPayloadForClassReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload(TEST_PAYLOAD_TYPE, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForClassReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message result = testSubject.withConvertedPayload(byte[].class, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metadata()).isEqualTo(result.metadata());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(STRING_PAYLOAD.getBytes()).isEqualTo(result.payload());
        //noinspection unchecked
        validateMessageSpecifics(testSubject, (M) result);
    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload(STRING_TYPE_REFERENCE, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message result = testSubject.withConvertedPayload(BYTE_ARRAY_TYPE_REF, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metadata()).isEqualTo(result.metadata());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(STRING_PAYLOAD.getBytes()).isEqualTo(result.payload());
        //noinspection unchecked
        validateMessageSpecifics(testSubject, (M) result);
    }

    @Test
    void withConvertedPayloadForTypeReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload((Type) TEST_PAYLOAD_TYPE, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForTypeReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message result = testSubject.withConvertedPayload((Type) byte[].class, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metadata()).isEqualTo(result.metadata());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(STRING_PAYLOAD.getBytes()).isEqualTo(result.payload());
        //noinspection unchecked
        validateMessageSpecifics(testSubject, (M) result);
    }
}
