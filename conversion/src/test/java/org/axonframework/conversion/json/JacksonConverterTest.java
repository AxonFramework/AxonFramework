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

package org.axonframework.conversion.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.common.TypeReference;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ConverterTestSuite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
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
    private static final TypeReference<List<SomeInput>> SOME_INPUT_LIST_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, SomeOtherInput>> SOME_OTHER_INPUT_MAP_TYPE_REF = new TypeReference<>() {
    };

    @Override
    protected JacksonConverter buildConverter() {
        return new JacksonConverter(OBJECT_MAPPER);
    }

    @Override
    protected Stream<Arguments> specificSameTypeConversions() {
        return Stream.empty();
    }

    @Override
    protected Stream<Arguments> specificConversionScenarios() {
        SomeInput someInput = new SomeInput("ID789", "JsonName", 1337);
        SomeOtherInput someOtherInput = new SomeOtherInput("USR003", "Json description");
        byte[] jsonCompliantBytes;
        try {
            jsonCompliantBytes = OBJECT_MAPPER.writeValueAsBytes("Lorem Ipsum");
        } catch (JsonProcessingException e) {
            fail("Could not write given variable to a JSON compliant byte array.");
            throw new RuntimeException(e);
        }
        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
        objectNode.put("property", "value");

        return Stream.of(
                arguments(someInput, SomeInput.class, JsonNode.class),
                arguments(someOtherInput, SomeOtherInput.class, JsonNode.class),
                arguments(List.of(someInput), SOME_INPUT_LIST_TYPE_REF.getType(), JsonNode.class),
                arguments(Map.of("USR003", someOtherInput), SOME_OTHER_INPUT_MAP_TYPE_REF.getType(), JsonNode.class),
                arguments(jsonCompliantBytes, byte[].class, JsonNode.class),
                arguments(objectNode, ObjectNode.class, JsonNode.class)
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

        assertThatThrownBy(() -> failingTestSubject.convert(testInput, SomeInput.class))
                .isExactlyInstanceOf(ConversionException.class);
    }
}
