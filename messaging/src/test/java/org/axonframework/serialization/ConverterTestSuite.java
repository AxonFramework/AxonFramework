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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite to validate {@link Converter} implementations.
 * <p>
 * As this test suite uses parameterized tests that requires <b>static</b> providers, the suite cannot enforce the
 * construction of those methods through {@code abstract} methods. Hence, implementers of the test suite should be
 * mindful to implement the following {@code static} methods:
 * <ol>
 *     <li>{@code supportedConversions}</li>
 *     <li>{@code unsupportedConversions}</li>
 *     <li>{@code sameTypeConversions}</li>
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

    @ParameterizedTest
    @MethodSource("unsupportedConversions")
    void canConvertReturnsFalseForUnsupportedConversions(Class<?> sourceType, Class<?> targetType) {
        assertThat(testSubject.canConvert(sourceType, targetType)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("sameTypeConversions")
    <I> void shouldReturnSameInstanceIfSourceAndTargetTypeAreEqual(I input, Class<I> eventType) {
        Object result = testSubject.convert(input, eventType, eventType);
        assertThat(result).isSameAs(input);
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
}
