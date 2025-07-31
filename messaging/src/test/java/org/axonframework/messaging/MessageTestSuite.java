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
 * @author Steven van Beelen
 */
public abstract class MessageTestSuite {

    private static final String STRING_PAYLOAD = "some-string-payload";
    private static final ChainingContentTypeConverter CONVERTER = new ChainingContentTypeConverter();
    private static final TypeReference<byte[]> BYTE_ARRAY_TYPE_REF = new TypeReference<>() {
    };

    /**
     * Builds a {@link Message} used by this test suite.
     *
     * @param <P> The payload type for the {@link Message} implementation under test.
     * @param <M> The {@link Message} implementation under test.
     * @return a {@link Message} used by this test suite.
     */
    protected abstract <P, M extends Message<P>> M buildMessage(P payload);

    @Test
    void payloadAsWithClassAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter testConverter = spy(CONVERTER);

        Message<String> testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs(String.class, testConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(testConverter);
    }

    @Test
    void payloadAsWithClassConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        Message<String> testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithClassThrowsNullPointerExceptionForNullConverter() {
        Message<String> testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(byte[].class, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadAsWithTypeReferenceConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        Message<String> testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithTypeReferenceThrowsNullPointerExceptionForNullConverter() {
        Message<String> testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs(BYTE_ARRAY_TYPE_REF, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadAsWithTypeInstanceOfClassAndAssignableFromPayloadTypeInvokesPayloadDirectly() {
        String testPayload = STRING_PAYLOAD;
        Converter testConverter = spy(CONVERTER);

        Message<String> testSubject = buildMessage(testPayload);

        String result = testSubject.payloadAs((Type) String.class, testConverter);

        assertThat(testPayload).isEqualTo(result);
        verifyNoInteractions(testConverter);
    }

    @Test
    void payloadAsWithTypeConvertsPayload() {
        String testPayload = STRING_PAYLOAD;

        Message<String> testSubject = buildMessage(testPayload);

        byte[] result = testSubject.payloadAs((Type) byte[].class, CONVERTER);

        assertThat(testPayload.getBytes()).isEqualTo(result);
    }

    @Test
    void payloadAsWithTypeThrowsNullPointerExceptionForNullConverter() {
        Message<String> testSubject = buildMessage(STRING_PAYLOAD);

        assertThatThrownBy(() -> testSubject.payloadAs((Type) byte[].class, null))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void withConvertedPayloadForClassReturnsSameInstance() {
        Message<String> testSubject = buildMessage(STRING_PAYLOAD);

        testSubject.withConvertedPayload(byte[].class, CONVERTER);
    }

    @Test
    void withConvertedPayloadForClassReturnsNewMessageInstanceWithConvertedPayload() {

    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsSameInstance() {

    }

    @Test
    void withConvertedPayloadForTypeReferenceReturnsNewMessageInstanceWithConvertedPayload() {

    }

    @Test
    void withConvertedPayloadForTypeReturnsSameInstance() {

    }

    @Test
    void withConvertedPayloadForTypeReturnsNewMessageInstanceWithConvertedPayload() {

    }
}
