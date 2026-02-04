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

package org.axonframework.messaging.core;

import org.axonframework.conversion.Converter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;

import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ConversionCache}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(MockitoExtension.class)
class ConversionCacheTest {

    private static final String ORIGINAL = "test-original";

    private ConversionCache testSubject;

    private Converter mockConverter;
    private Type mockTargetType;

    @BeforeEach
    void setUp() {
        mockConverter = mock(Converter.class);
        mockTargetType = mock(Type.class);

        testSubject = new ConversionCache(ORIGINAL);
    }

    @AfterEach
    void tearDown() {
        System.setProperty("AXON_CONVERSION_CACHE_ENABLED", "true");
    }

    @Test
    void convertIfAbsentConvertsWhenNotCached() {
        String expectedResult = "converted-result";
        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn(expectedResult);

        String result = testSubject.convertIfAbsent(mockTargetType, mockConverter);

        assertThat(result).isEqualTo(expectedResult);
        verify(mockConverter).convert(ORIGINAL, mockTargetType);
    }

    @Test
    void convertIfAbsentReturnsCachedResultOnSubsequentCalls() {
        String expectedResult = "cached-result";
        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn(expectedResult);

        // First call - should perform conversion
        String firstResult = testSubject.convertIfAbsent(mockTargetType, mockConverter);
        // Second call - should return cached result
        String secondResult = testSubject.convertIfAbsent(mockTargetType, mockConverter);

        assertThat(firstResult).isEqualTo(expectedResult);
        assertThat(secondResult).isEqualTo(expectedResult);
        assertThat(firstResult).isSameAs(secondResult);

        // Converter should only be called once
        verify(mockConverter, times(1)).convert(ORIGINAL, mockTargetType);
    }

    @Test
    void convertIfAbsentHandlesNullConversionResult() {
        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn(null);

        // First call
        String firstResult = testSubject.convertIfAbsent(mockTargetType, mockConverter);

        // Second call - should return cached null
        String secondResult = testSubject.convertIfAbsent(mockTargetType, mockConverter);

        assertThat(firstResult).isNull();
        assertThat(secondResult).isNull();
        // Converter should only be called once
        verify(mockConverter, times(1)).convert(ORIGINAL, mockTargetType);
    }

    @Test
    void convertIfAbsentDifferentiatesBetweenDifferentTargetTypes() {
        Type anotherTargetType = mock(Type.class);

        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn("result1");
        when(mockConverter.convert(ORIGINAL, anotherTargetType))
                .thenReturn("result2");

        String result1 = testSubject.convertIfAbsent(mockTargetType, mockConverter);
        String result2 = testSubject.convertIfAbsent(anotherTargetType, mockConverter);

        assertThat(result1).isEqualTo("result1");
        assertThat(result2).isEqualTo("result2");

        verify(mockConverter).convert(ORIGINAL, mockTargetType);
        verify(mockConverter).convert(ORIGINAL, anotherTargetType);
    }

    @Test
    void convertIfAbsentDifferentiatesBetweenDifferentConverters() {
        Converter anotherConverter = mock(Converter.class);

        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn("result1");
        when(anotherConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn("result2");

        String result1 = testSubject.convertIfAbsent(mockTargetType, mockConverter);
        String result2 = testSubject.convertIfAbsent(mockTargetType, anotherConverter);

        assertThat(result1).isEqualTo("result1");
        assertThat(result2).isEqualTo("result2");

        verify(mockConverter).convert(ORIGINAL, mockTargetType);
        verify(anotherConverter).convert(ORIGINAL, mockTargetType);
    }

    @Test
    void convertIfAbsentWorkWithNullOriginalObject() {
        ConversionCache testSubjectWithNullOriginal = new ConversionCache(null);
        String expectedResult = "converted-null";

        when(mockConverter.convert(null, mockTargetType))
                .thenReturn(expectedResult);

        String result = testSubjectWithNullOriginal.convertIfAbsent(mockTargetType, mockConverter);

        assertThat(result).isEqualTo(expectedResult);
        verify(mockConverter).convert(null, mockTargetType);
    }

    @Test
    void convertIfAbsentPerformsConversionAlwaysWhenCacheDisabled() {
        System.setProperty("AXON_CONVERSION_CACHE_ENABLED", "false");
        ConversionCache cachingDisabledSubject = new ConversionCache(ORIGINAL);

        String expectedResult = "direct-conversion";
        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn(expectedResult);

        String firstResult = cachingDisabledSubject.convertIfAbsent(mockTargetType, mockConverter);
        String secondResult = cachingDisabledSubject.convertIfAbsent(mockTargetType, mockConverter);

        assertThat(firstResult).isEqualTo(expectedResult);
        assertThat(secondResult).isEqualTo(expectedResult);
        assertThat(firstResult).isSameAs(secondResult);

        // Converter should be called twice because caching is disabled
        verify(mockConverter, times(2)).convert(ORIGINAL, mockTargetType);
    }

    @Test
    void convertIfAbsentHandlesConcurrentAccessSafely() throws InterruptedException {
        String expectedResult = "thread-safe-result";
        when(mockConverter.convert(ORIGINAL, mockTargetType))
                .thenReturn(expectedResult);

        int numberOfThreads = 10;
        Thread[] threads = new Thread[numberOfThreads];
        String[] results = new String[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            int threadIndex = i;
            threads[i] = new Thread(
                    () -> results[threadIndex] = testSubject.convertIfAbsent(mockTargetType, mockConverter)
            );
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All results should be the same
        assertThat(results)
                .allMatch(expectedResult::equals);

        // Converter should only be called once due to caching
        verify(mockConverter, times(1)).convert(ORIGINAL, mockTargetType);
    }
}