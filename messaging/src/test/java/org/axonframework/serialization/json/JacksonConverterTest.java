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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.ConverterTestSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link JacksonConverter}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
class JacksonConverterTest extends ConverterTestSuite<JacksonConverter> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    protected JacksonConverter buildConverter() {
        return new JacksonConverter(OBJECT_MAPPER);
    }

    @SuppressWarnings("unused") // Used by ConverterTestSuite
    static Stream<Arguments> supportedConversions() {
        return Stream.of(
                // Convert from concrete type:
                arguments(SomeInput.class, byte[].class),
                arguments(SomeInput.class, String.class),
                arguments(SomeInput.class, InputStream.class),
                arguments(SomeInput.class, JsonNode.class),
                arguments(SomeInput.class, ObjectNode.class),
                // Convert to concrete type:
                arguments(byte[].class, SomeInput.class),
                arguments(String.class, SomeInput.class),
                arguments(InputStream.class, SomeInput.class),
                arguments(JsonNode.class, SomeInput.class),
                arguments(ObjectNode.class, SomeInput.class),
                // Convert from another concrete type:
                arguments(SomeOtherInput.class, String.class),
                arguments(SomeOtherInput.class, byte[].class),
                arguments(SomeOtherInput.class, InputStream.class),
                arguments(SomeOtherInput.class, JsonNode.class),
                arguments(SomeOtherInput.class, ObjectNode.class),
                // Intermediate conversion levels:
                arguments(String.class, JsonNode.class),
                arguments(JsonNode.class, String.class),
                arguments(ObjectNode.class, JsonNode.class),
                arguments(ObjectNode.class, String.class),
                arguments(JsonNode.class, ObjectNode.class),
                arguments(String.class, ObjectNode.class),
                arguments(byte[].class, ObjectNode.class),
                arguments(ObjectNode.class, byte[].class),
                // Same type:
                arguments(SomeInput.class, SomeInput.class),
                arguments(SomeOtherInput.class, SomeOtherInput.class),
                arguments(byte[].class, byte[].class),
                arguments(String.class, String.class),
                arguments(JsonNode.class, JsonNode.class),
                arguments(ObjectNode.class, ObjectNode.class)
        );
    }

    @SuppressWarnings("unused") // Used by ConverterTestSuite
    static Stream<Arguments> unsupportedConversions() {
        return Stream.of(
                arguments(SomeInput.class, Integer.class),
                arguments(SomeOtherInput.class, Double.class),
                arguments(Integer.class, SomeInput.class),
                arguments(Double.class, SomeOtherInput.class)
        );
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

    @SuppressWarnings("unused") // Used by ConverterTestSuite
    static Stream<Arguments> conversionScenarios() throws JsonProcessingException {
        byte[] jsonCompliantBytes = OBJECT_MAPPER.writeValueAsBytes("Lorem Ipsum");
        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
        objectNode.put("property", "value");
        return Stream.of(
                arguments(new SomeInput("ID123", "TestName", 42), String.class, SomeInput.class),
                arguments(new SomeInput("ID456", "OtherName", 99), byte[].class, SomeInput.class),
                arguments(new SomeInput("ID789", "JsonName", 1337), JsonNode.class, SomeInput.class),
                arguments(new SomeOtherInput("USR001", "Some description"), String.class, SomeOtherInput.class),
                arguments(new SomeOtherInput("USR002", "Another description"), byte[].class, SomeOtherInput.class),
                arguments(new SomeOtherInput("USR003", "Json description"), JsonNode.class, SomeOtherInput.class),
                arguments("Lorem Ipsum", byte[].class, String.class),
                arguments("Lorem Ipsum".getBytes(StandardCharsets.UTF_8), InputStream.class, byte[].class),
                arguments(jsonCompliantBytes, JsonNode.class, byte[].class),
                arguments(objectNode, JsonNode.class, ObjectNode.class)
        );
    }

    @Test
    void convertThrowsConversionExceptionOnIOExceptionFromObjectMapper() throws IOException {
        ObjectMapper mockedObjectMapper = mock(ObjectMapper.class);
        when(mockedObjectMapper.constructType(SomeInput.class))
                .thenReturn(OBJECT_MAPPER.constructType(SomeInput.class));
        when(mockedObjectMapper.readValue((byte[]) any(), (JavaType) any())).thenThrow(new IOException());

        JacksonConverter failingTestSubject = new JacksonConverter(mockedObjectMapper);

        byte[] testInput = OBJECT_MAPPER.writeValueAsBytes(new SomeInput("id", "name", 42));

        assertThatThrownBy(() -> failingTestSubject.convert(testInput, byte[].class, SomeInput.class))
                .isExactlyInstanceOf(ConversionException.class);
    }

    record SomeInput(String id, String name, int value) {

    }

    record SomeOtherInput(String userId, String description) {

    }
}
