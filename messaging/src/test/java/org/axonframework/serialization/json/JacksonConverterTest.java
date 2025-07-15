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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link JacksonConverter}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
class JacksonConverterTest {

    private JacksonConverter converter;

    record TestEvent(String id, String name, int value) {}
    record AnotherEvent(String userId, String description) {}

    @BeforeEach
    void setUp() {
        converter = new JacksonConverter();
    }

    @Test
    void shouldConvertTestEventToStringAndBack() {
        // given
        TestEvent original = new TestEvent("ID123", "TestName", 42);

        // when
        String serialized = converter.convert(original, String.class);
        TestEvent deserialized = converter.convert(serialized, TestEvent.class);

        // then
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void shouldConvertTestEventToByteArrayAndBack() {
        // given
        TestEvent original = new TestEvent("ID456", "OtherName", 99);

        // when
        byte[] serialized = converter.convert(original, byte[].class);
        TestEvent deserialized = converter.convert(serialized, TestEvent.class);

        // then
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void shouldConvertAnotherEventToStringAndBack() {
        // given
        AnotherEvent original = new AnotherEvent("USR001", "Some description");

        // when
        String serialized = converter.convert(original, String.class);
        AnotherEvent deserialized = converter.convert(serialized, AnotherEvent.class);

        // then
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void shouldReturnTrueForSupportedConversions() {
        // Convert from concrete type:
        assertThat(converter.canConvert(TestEvent.class, byte[].class)).isTrue();
        assertThat(converter.canConvert(TestEvent.class, String.class)).isTrue();
        assertThat(converter.canConvert(TestEvent.class, InputStream.class)).isTrue();
        assertThat(converter.canConvert(TestEvent.class, JsonNode.class)).isTrue();
        assertThat(converter.canConvert(TestEvent.class, ObjectNode.class)).isTrue();
        // Convert to concrete type:
        assertThat(converter.canConvert(byte[].class, TestEvent.class)).isTrue();
        assertThat(converter.canConvert(String.class, TestEvent.class)).isTrue();
        assertThat(converter.canConvert(InputStream.class, TestEvent.class)).isTrue();
        assertThat(converter.canConvert(JsonNode.class, TestEvent.class)).isTrue();
        assertThat(converter.canConvert(ObjectNode.class, TestEvent.class)).isTrue();
        // Convert from another concrete type:
        assertThat(converter.canConvert(AnotherEvent.class, String.class)).isTrue();
        assertThat(converter.canConvert(AnotherEvent.class, byte[].class)).isTrue();
        assertThat(converter.canConvert(AnotherEvent.class, InputStream.class)).isTrue();
        assertThat(converter.canConvert(AnotherEvent.class, JsonNode.class)).isTrue();
        assertThat(converter.canConvert(AnotherEvent.class, ObjectNode.class)).isTrue();
        // Intermediate conversion levels:
        assertThat(converter.canConvert(String.class, JsonNode.class)).isTrue();
        assertThat(converter.canConvert(JsonNode.class, String.class)).isTrue();
        assertThat(converter.canConvert(ObjectNode.class, JsonNode.class)).isTrue();
        assertThat(converter.canConvert(ObjectNode.class, String.class)).isTrue();
        assertThat(converter.canConvert(JsonNode.class, ObjectNode.class)).isTrue();
        assertThat(converter.canConvert(String.class, ObjectNode.class)).isTrue();
        assertThat(converter.canConvert(byte[].class, ObjectNode.class)).isTrue();
        assertThat(converter.canConvert(ObjectNode.class, byte[].class)).isTrue();
    }

    @Test
    void shouldReturnFalseForUnsupportedConversions() {
        assertThat(converter.canConvert(TestEvent.class, Integer.class)).isFalse();
        assertThat(converter.canConvert(AnotherEvent.class, Double.class)).isFalse();
        assertThat(converter.canConvert(Integer.class, TestEvent.class)).isFalse();
        assertThat(converter.canConvert(Double.class, AnotherEvent.class)).isFalse();
    }

    @Test
    void shouldReturnSameInstanceIfSourceAndTargetTypeAreEqual() {
        TestEvent testEvent = new TestEvent("ID789", "SameType", 123);
        TestEvent result = converter.convert(testEvent, TestEvent.class, TestEvent.class);
        assertThat(result).isSameAs(testEvent);

        AnotherEvent anotherEvent = new AnotherEvent("USR002", "No conversion");
        AnotherEvent anotherResult = converter.convert(anotherEvent, AnotherEvent.class, AnotherEvent.class);
        assertThat(anotherResult).isSameAs(anotherEvent);
    }
}
