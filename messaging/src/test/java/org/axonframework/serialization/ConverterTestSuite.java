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

package org.axonframework.serialization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.*;

/**
 * Test suite to validate {@link Converter} implementations.
 * <p>
 * As this test suite uses parameterized tests that requires <b>static</b> providers, the suite cannot enforce the
 * construction of those methods through {@code abstract} methods. Hence, implementers of the test suite should be
 * mindful to implement the following {@code static} methods:
 * <ol>
 *     <li>{@code supportedConversions}</li>
 *     <li>{@code conversionScenarios}</li>
 * </ol>
 * Forgetting to do so will result in a {@link org.junit.platform.commons.PreconditionViolationException} when running the tests.
 *
 * @author Steven van Beelen.
 */
public abstract class ConverterTestSuite<C extends Converter> {

    protected C testSubject;

    @BeforeEach
    void setUp() {
        this.testSubject = buildConverter();
    }

    /**
     * Builds the {@link Converter} used in this test suite.
     *
     * @return The {@link Converter} used in this test suite.
     */
    protected abstract C buildConverter();

    @ParameterizedTest
    @MethodSource("supportedConversions")
    void canConvertReturnsTrueForSupportedConversions(Class<?> sourceType, Class<?> targetType) {
        assertThat(testSubject.canConvert(sourceType, targetType)).isTrue();
    }

    @SuppressWarnings("unused") // Used by child classes
    protected static Stream<Arguments> commonConversions() {
        return Stream.of(
                // Convert from concrete type:
                arguments(SomeInput.class, byte[].class),
                arguments(SomeInput.class, String.class),
                arguments(SomeInput.class, InputStream.class),
                // Convert to concrete type:
                arguments(byte[].class, SomeInput.class),
                arguments(String.class, SomeInput.class),
                arguments(InputStream.class, SomeInput.class),
                // Convert from another concrete type:
                arguments(SomeOtherInput.class, String.class),
                arguments(SomeOtherInput.class, byte[].class),
                arguments(SomeOtherInput.class, InputStream.class),
                // Intermediate conversion levels:
                arguments(String.class, byte[].class),
                arguments(byte[].class, String.class),
                arguments(byte[].class, InputStream.class),
                arguments(InputStream.class, byte[].class),
                arguments(String.class, InputStream.class),
                arguments(InputStream.class, String.class),
                // Same type:
                arguments(SomeInput.class, SomeInput.class),
                arguments(SomeOtherInput.class, SomeOtherInput.class),
                arguments(byte[].class, byte[].class),
                arguments(String.class, String.class)
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedConversions")
    void canConvertReturnsFalseForUnsupportedConversions(Class<?> sourceType, Class<?> targetType) {
        assertThat(testSubject.canConvert(sourceType, targetType)).isFalse();
    }

    static Stream<Arguments> unsupportedConversions() {
        return Stream.of(
                arguments(SomeInput.class, Integer.class),
                arguments(SomeOtherInput.class, Double.class),
                arguments(Integer.class, SomeInput.class),
                arguments(Double.class, SomeOtherInput.class)
        );
    }

    @ParameterizedTest
    @MethodSource("sameTypeConversions")
    <I> void shouldReturnSameInstanceIfSourceAndTargetTypeAreEqual(I input, Class<I> eventType) {
        Object result = testSubject.convert(input, eventType, eventType);
        assertThat(result).isSameAs(input);
    }

    @SuppressWarnings("unused") // Used by ConverterTestSuite
    static Stream<Arguments> sameTypeConversions() {
        return Stream.of(
                arguments("Lorem Ipsum", String.class),
                arguments(42L, Long.class),
                arguments(new SomeInput("ID789", "SameType", 123), SomeInput.class),
                arguments(new SomeOtherInput("USR002", "No conversion"), SomeOtherInput.class)
        );
    }

    @Test
    <I> void convertForTargetTypeReturnsNullForNullInput() {
        Object result = testSubject.convert((I) null, Object.class);
        assertThat(result).isNull();

        result = testSubject.convert((I) null, Object.class);
        assertThat(result).isNull();
    }

    @Test
    void convertForSourceAndTargetTypeReturnsNullForNullInput() {
        Object result = testSubject.convert(null, Object.class, Object.class);
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @MethodSource("conversionScenarios")
    <I, O> void convertForTargetTypeCanConvertBackToSource(I input,
                                                           Class<O> targetType) {
        O targetConversion = testSubject.convert(input, targetType);
        Object sourceConversion = testSubject.convert(targetConversion, input.getClass());
        assertThat(sourceConversion).isEqualTo(input);
    }

    @ParameterizedTest
    @MethodSource("conversionScenarios")
    <I, O> void convertForSourceAndTargetTypeCanConvertBackToSource(I input,
                                                                    Class<O> targetType,
                                                                    Class<I> sourceType) {
        O targetConversion = testSubject.convert(input, sourceType, targetType);
        I sourceConversion = testSubject.convert(targetConversion, sourceType);
        assertThat(sourceConversion).isEqualTo(input);
    }

    @SuppressWarnings("unused") // Used by ConverterTestSuite
    protected static Stream<Arguments> commonConversionScenarios() {
        return Stream.of(
                arguments(new SomeInput("ID123", "TestName", 42), String.class, SomeInput.class),
                arguments(new SomeInput("ID456", "OtherName", 99), byte[].class, SomeInput.class),
                arguments(new SomeOtherInput("USR001", "Some description"), String.class, SomeOtherInput.class),
                arguments(new SomeOtherInput("USR002", "Another description"), byte[].class, SomeOtherInput.class),
                arguments("Lorem Ipsum", byte[].class, String.class),
                arguments("Lorem Ipsum".getBytes(StandardCharsets.UTF_8), InputStream.class, byte[].class)
        );
    }

    public record SomeInput(String id, String name, int value) {

    }

    public record SomeOtherInput(String userId, String description) {

    }
}
