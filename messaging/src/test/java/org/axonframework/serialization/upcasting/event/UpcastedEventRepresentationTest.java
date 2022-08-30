/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.serialization.upcasting.event;

import com.fasterxml.jackson.databind.JsonNode;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test class validating the {@link UpcastedEventRepresentation}.
 *
 * @author Steven van Beelen
 */
class UpcastedEventRepresentationTest {

    private static final String SOURCE_METHOD_NAME = "serializer";

    @SuppressWarnings("unused") // Used by parameterized test "testContentType"
    private static Stream<Arguments> serializer() {
        return Stream.of(
                Arguments.of(TestSerializer.XSTREAM.getSerializer()),
                Arguments.of(TestSerializer.JACKSON.getSerializer()),
                Arguments.of(TestSerializer.JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS.getSerializer())
        );
    }

    @ParameterizedTest
    @MethodSource(SOURCE_METHOD_NAME)
    void contentType(Serializer serializer) {
        Class<JsonNode> expectedContentType = JsonNode.class;

        UpcastedEventRepresentation<JsonNode> testSubject = new UpcastedEventRepresentation<>(
                mock(SerializedType.class), mock(IntermediateEventRepresentation.class),
                o -> o, m -> m, expectedContentType, serializer.getConverter()
        );

        assertEquals(expectedContentType, testSubject.getContentType());
    }
}