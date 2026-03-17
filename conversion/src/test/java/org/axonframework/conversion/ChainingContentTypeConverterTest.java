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

package org.axonframework.conversion;

import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.*;

/**
 * Test class validating the {@link ChainingContentTypeConverter}.
 *
 * @author Steven van Beelen
 */
class ChainingContentTypeConverterTest extends ConverterTestSuite<ChainingContentTypeConverter> {

    @Override
    protected ChainingContentTypeConverter buildConverter() {
        return new ChainingContentTypeConverter();
    }

    @ParameterizedTest
    @MethodSource("supportedConversions")
    void canConvertReturnsTrueForSupportedConversions(Type sourceType, Type targetType) {
        assertThat(testSubject.canConvert(sourceType, targetType)).isTrue();
    }

    private Stream<Arguments> supportedConversions() {
        return Stream.of(
                // Intermediate conversion levels:
                arguments(String.class, byte[].class),
                arguments(byte[].class, String.class),
                arguments(byte[].class, InputStream.class),
                arguments(InputStream.class, byte[].class),
                arguments(String.class, InputStream.class),
                arguments(InputStream.class, String.class),
                // Same type:
                arguments(byte[].class, byte[].class),
                arguments(String.class, String.class)
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedConversions")
    void canConvertReturnsFalseForUnsupportedConversions(Type sourceType, Type targetType) {
        assertThat(testSubject.canConvert(sourceType, targetType)).isFalse();
    }

    private Stream<Arguments> unsupportedConversions() {
        return Stream.of(
                arguments(SomeInput.class, Integer.class),
                arguments(SomeOtherInput.class, Double.class),
                arguments(Integer.class, SomeInput.class),
                arguments(Double.class, SomeOtherInput.class)
        );
    }

    @Override
    protected Stream<Arguments> specificSameTypeConversions() {
        // The ChainingContentTypeConverter does not have any specific same type conversion scenarios.
        return Stream.empty();
    }

    @Override
    protected Stream<Arguments> specificConversionScenarios() {
        // The ChainingContentTypeConverter does not have any specific conversion scenarios.
        return Stream.empty();
    }

    @Override
    protected Stream<Arguments> commonConversionScenarios() {
        return Stream.of(
                arguments("Lorem Ipsum", String.class, byte[].class),
                arguments("Lorem Ipsum".getBytes(StandardCharsets.UTF_8), byte[].class, InputStream.class)
        );
    }
}