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

import org.junit.jupiter.params.provider.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

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

    @Override
    protected Stream<Arguments> commonSupportedConversions() {
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

    @Override
    protected Stream<Arguments> specificSupportedConversions() {
        // The ChainingContentTypeConverter does not have any specific supported conversion scenarios.
        return Stream.empty();
    }

    @Override
    protected Stream<Arguments> specificUnsupportedConversions() {
        // The ChainingContentTypeConverter does not have any specific unsupported conversion scenarios.
        return Stream.empty();
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
                arguments("Lorem Ipsum", byte[].class, String.class),
                arguments("Lorem Ipsum".getBytes(StandardCharsets.UTF_8), InputStream.class, byte[].class)
        );
    }
}