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
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test suite to validate {@link Message} implementation.
 *
 * @param <M> The {@link Message} implementation under test.
 * @author Steven van Beelen
 */
public abstract class MessageTestSuite<M extends Message<?>> {

    private static final String STRING_PAYLOAD = "some-string-payload";
    private static final ChainingContentTypeConverter CONVERTER = new ChainingContentTypeConverter();
    private static final TypeReference<byte[]> BYTE_ARRAY_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<String> STRING_TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * Builds a {@link Message} used by this test suite.
     *
     * @param <P> The payload type for the {@link Message} implementation under test.
     * @return a {@link Message} used by this test suite.
     */
    protected abstract <P> M buildMessage(@Nullable P payload);

    @Test
    void payloadAsWithClassAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter testConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs(String.class, testConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(testConverter);
    }

    @Test
    void payloadAsWithClassConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithClassThrowsNullPointerExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(byte[].class, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadAsWithTypeReferenceConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithTypeReferenceThrowsNullPointerExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadAsWithTypeInstanceOfClassAndAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter testConverter = spy(CONVERTER);

        M testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs((Type) String.class, testConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(testConverter);
    }

    @Test
    void payloadAsWithTypeConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        M testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs((Type) byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithTypeThrowsNullPointerExceptionForNullConverter() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs((Type) byte[].class, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void withConvertedPayloadForClassReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload(String.class, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForClassReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message<byte[]> result = testSubject.withConvertedPayload(byte[].class, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metaData()).isEqualTo(result.metaData());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(testSubject.payload().getBytes()).isEqualTo(result.payload());
    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload(STRING_TYPE_REFERENCE, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message<byte[]> result = testSubject.withConvertedPayload(BYTE_ARRAY_TYPE_REF, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metaData()).isEqualTo(result.metaData());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(testSubject.payload().getBytes()).isEqualTo(result.payload());
    }

    @Test
    void withConvertedPayloadForTypeReturnsSameInstance() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        assertThat(testSubject.withConvertedPayload((Type) String.class, CONVERTER)).isSameAs(testSubject);
    }

    @Test
    void withConvertedPayloadForTypeReturnsNewMessageInstanceWithConvertedPayload() {
        M testSubject = buildMessage(STRING_PAYLOAD);

        Message<byte[]> result = testSubject.withConvertedPayload((Type) byte[].class, CONVERTER);

        assertThat(testSubject.identifier()).isEqualTo(result.identifier());
        assertThat(testSubject.type()).isEqualTo(result.type());
        assertThat(testSubject.metaData()).isEqualTo(result.metaData());
        assertThat(testSubject.payloadType()).isNotEqualTo(result.payloadType());
        assertThat(testSubject.payload().getBytes()).isEqualTo(result.payload());
    }
}
