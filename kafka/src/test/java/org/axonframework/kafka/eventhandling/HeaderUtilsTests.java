/*
 * Copyright (c) 2010-2018. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.junit.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.kafka.eventhandling.HeaderUtils.addHeader;
import static org.axonframework.kafka.eventhandling.HeaderUtils.byteMapper;
import static org.axonframework.kafka.eventhandling.HeaderUtils.extractAxonMetadata;
import static org.axonframework.kafka.eventhandling.HeaderUtils.extractKey;
import static org.axonframework.kafka.eventhandling.HeaderUtils.generateMetadataKey;
import static org.axonframework.kafka.eventhandling.HeaderUtils.keys;
import static org.axonframework.kafka.eventhandling.HeaderUtils.toHeaders;
import static org.axonframework.kafka.eventhandling.HeaderUtils.value;
import static org.axonframework.kafka.eventhandling.HeaderUtils.valueAsLong;
import static org.axonframework.kafka.eventhandling.HeaderUtils.valueAsString;
import static org.axonframework.kafka.eventhandling.HeaderAssertUtils.assertDomainHeaders;
import static org.axonframework.kafka.eventhandling.HeaderAssertUtils.assertEventHeaders;
import static org.axonframework.messaging.Headers.MESSAGE_METADATA;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link HeaderUtils}
 *
 * @author Nakul Mishra
 */
public class HeaderUtilsTests {

    @Test
    public void testReadingRawValueFromHeaderWith_ValidKey() {
        RecordHeaders headers = new RecordHeaders();
        String value = "a1b2";
        addHeader(headers, "bar", value);
        assertThat(value(headers, "bar"), is(value.getBytes()));
    }

    @Test
    public void testReadingRawValueFromHeaderWith_InvalidKey() {
        assertThat(value(new RecordHeaders(), "123"), is(nullValue()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadingValueFromAnInvalidHeader() {
        value(null, "bar");
    }

    @Test
    public void testReadingTextValueFromHeaderWith_ValidKey() {
        RecordHeaders headers = new RecordHeaders();
        String expectedValue = "Şơм℮ śẩмρŀę ÅŚÇÍỈ-ťęҳť FFlETYeKU3H5QRqw";
        addHeader(headers, "foo", expectedValue);
        assertThat(valueAsString(headers, "foo"), is(expectedValue));
        assertThat(valueAsString(headers, "foo", "default-value"), is(expectedValue));
    }

    @Test
    public void testReadingTextValueFromHeaderWith_InvalidKey() {
        assertThat(valueAsString(new RecordHeaders(), "some-invalid-key"), is(nullValue()));
        assertThat(valueAsString(new RecordHeaders(), "some-invalid-key", "default-value"), is("default-value"));
    }

    @Test
    public void testReadingLongValuesFromHeaderWith_ValidKey() {
        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, "positive", 4_891_00_921_388_62621L);
        addHeader(headers, "zero", 0L);
        addHeader(headers, "negative", -4_8912_00_921_388_62621L);

        assertThat(valueAsLong(headers, "positive"), is(4_891_00_921_388_62621L));
        assertThat(valueAsLong(headers, "zero"), is(0L));
        assertThat(valueAsLong(headers, "negative"), is(-4_8912_00_921_388_62621L));
    }

    @Test
    public void testReadingLongValueFromHeaderWith_InvalidKey() {
        assertThat(valueAsLong(new RecordHeaders(), "some-invalid-key"), is(nullValue()));
    }

    @Test
    public void testWritingTimestampInHeader() {
        RecordHeaders target = new RecordHeaders();
        Instant value = Instant.now();
        addHeader(target, "baz", value);
        assertThat(valueAsLong(target, "baz"), is(value.toEpochMilli()));
    }

    @Test
    public void testWritingNonNegativeValuesInHeader() {
        RecordHeaders target = new RecordHeaders();
        short expectedShort = 1;
        int expectedInt = 200;
        long expectedLong = 300L;
        float expectedFloat = 300.f;
        double expectedDouble = 0.000;
        addHeader(target, "short", expectedShort);
        assertThat(shortValue(target), is(expectedShort));

        addHeader(target, "int", expectedInt);
        assertThat(intValue(target), is(expectedInt));

        addHeader(target, "long", expectedLong);
        assertThat(longValue(target), is(expectedLong));

        addHeader(target, "float", expectedFloat);
        assertThat(floatValue(target), is(expectedFloat));

        addHeader(target, "double", expectedDouble);
        assertThat(doubleValue(target), is(expectedDouble));
    }

    @Test
    public void testWritingNegativeValuesInHeader() {
        RecordHeaders target = new RecordHeaders();
        short expectedShort = -123;
        int expectedInt = -1_234_567_8;
        long expectedLong = -1_234_567_89_0L;
        float expectedFloat = -1_234_567_89_0.0f;
        double expectedDouble = -1_234_567_89_0.987654321;
        addHeader(target, "short", expectedShort);
        assertThat(shortValue(target), is(expectedShort));

        addHeader(target, "int", expectedInt);
        assertThat(intValue(target), is(expectedInt));

        addHeader(target, "long", expectedLong);
        assertThat(longValue(target), is(expectedLong));

        addHeader(target, "float", expectedFloat);
        assertThat(floatValue(target), is(expectedFloat));

        addHeader(target, "double", expectedDouble);
        assertThat(doubleValue(target), is(expectedDouble));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWritingNonPrimitiveValueInHeader() {
        addHeader(new RecordHeaders(), "short", BigInteger.ZERO);
    }

    @Test
    public void testWritingTextValuesInHeader() {
        RecordHeaders target = new RecordHeaders();
        String expectedKey = "foo";
        String expectedValue = "a";
        addHeader(target, expectedKey, expectedValue);

        assertThat(target.toArray().length, is(1));
        assertThat(target.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(valueAsString(target, expectedKey), is(expectedValue));
    }

    @Test
    public void testWritingNullValueInHeader() {
        RecordHeaders target = new RecordHeaders();
        addHeader(target, "baz", null);
        assertThat(value(target, "baz"), is(nullValue()));
    }

    @Test
    public void testWritingCustomValueInHeader() {
        RecordHeaders target = new RecordHeaders();
        Foo expectedValue = new Foo("someName", new Bar(100));
        addHeader(target, "object", expectedValue);
        assertThat(valueAsString(target, "object"), is(expectedValue.toString()));
    }

    @Test
    public void testExtractingKeysFromHeaders() {
        RecordHeaders target = new RecordHeaders();
        addHeader(target, "a", "someValue");
        addHeader(target, "b", "someValue");
        addHeader(target, "c", "someValue");
        Set<String> expectedKeys = new HashSet<>();
        target.forEach(header -> expectedKeys.add(header.key()));
        assertThat(keys(target), equalTo(expectedKeys));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractingKeysFromInvalidHeaders() {
        keys(null);
    }

    @Test
    public void testGeneratingKeyUseToSendAxonMetadataToKafka() {
        assertThat(generateMetadataKey("foo"), is(MESSAGE_METADATA + "-foo"));
        assertThat(generateMetadataKey(null), is(MESSAGE_METADATA + "-null"));
    }

    @Test
    public void testExtractingKeyThatWasUsedToSendAxonMetadataToKafka() {
        assertThat(extractKey(generateMetadataKey("foo")), is("foo"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractingKeyFromInvalidMetadataKey() {
        extractKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractingKeyFromNonMetadataKey() {
        extractKey("foo-bar-axon-metadata");
    }

    @Test
    public void testExtractingAxonMetadataFromHeader() {
        RecordHeaders target = new RecordHeaders();
        String key = generateMetadataKey("headerKey");
        String value = "abc";
        Map<String, Object> expectedValue = new HashMap<String, Object>() {{
            put("headerKey", value);
        }};
        addHeader(target, key, value);
        assertThat(extractAxonMetadata(target), is(expectedValue));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractingAxonMetadataFromInvalidHeader() {
        extractAxonMetadata(null);
    }

    @Test
    public void testGeneratingHeadersForEventMessage() {
        String metaKey = "someHeaderKey";
        EventMessage<Object> evt = asEventMessage("SomePayload").withMetaData(
                MetaData.with(metaKey, "someValue")
        );
        SerializedObject<byte[]> so = serializedObject();
        Headers headers = toHeaders(evt, so, byteMapper());
        assertEventHeaders(metaKey, evt, so, headers);
    }

    @Test
    public void testGeneratingHeadersForDomainMessage() {
        String metaKey = "someHeaderKey";
        DomainEventMessage<Object> evt = new GenericDomainEventMessage<>("Stub",
                                                                         "axc123-v",
                                                                         1L,
                                                                         "Payload",
                                                                         MetaData.with("key", "value"));
        SerializedObject<byte[]> so = serializedObject();
        Headers headers = toHeaders(evt, so, byteMapper());
        assertEventHeaders(metaKey, evt, so, headers);
        assertDomainHeaders(evt, headers);
    }

    @Test
    public void testGeneratingHeadersWithByteMapper() {
        BiFunction<String, Object, RecordHeader> fxn = byteMapper();
        String expectedKey = "abc";
        String expectedValue = "xyz";
        RecordHeader header = fxn.apply(expectedKey, expectedValue);
        assertThat(header.key(), is(expectedKey));
        assertThat(new String(header.value()), is(expectedValue));
    }

    @Test
    public void testByteMapperShouldAbleToHandleNullValues() {
        BiFunction<String, Object, RecordHeader> fxn = byteMapper();
        RecordHeader header = fxn.apply("abc", null);
        assertThat(header.value(), is(nullValue()));
    }

    @Test
    public void testGeneratingHeadersWithCustomMapper() {
        String metaKey = "someHeaderKey";
        String expectedMetaDataValue = "evt:someValue";
        Headers header = toHeaders(
                asEventMessage("SomePayload").withMetaData(
                        MetaData.with(metaKey, "someValue")
                ),
                serializedObject(),
                (key, value) -> new RecordHeader(key, ("evt:" + value.toString()).getBytes())
        );
        assertThat(valueAsString(header, generateMetadataKey(metaKey)), is(expectedMetaDataValue));
    }

    private double doubleValue(RecordHeaders target) {
        return ByteBuffer.wrap(Objects.requireNonNull(value(target, "double"))).getDouble();
    }

    private float floatValue(RecordHeaders target) {
        return ByteBuffer.wrap(Objects.requireNonNull(value(target, "float"))).getFloat();
    }

    private long longValue(RecordHeaders target) {
        return ByteBuffer.wrap(Objects.requireNonNull(value(target, "long"))).getLong();
    }

    private int intValue(RecordHeaders target) {
        return ByteBuffer.wrap(Objects.requireNonNull(value(target, "int"))).getInt();
    }

    private short shortValue(RecordHeaders target) {
        return ByteBuffer.wrap(Objects.requireNonNull(value(target, "short"))).getShort();
    }

    @SuppressWarnings("unchecked")
    private SerializedObject<byte[]> serializedObject() {
        SerializedObject serializedObject = mock(SerializedObject.class);
        when(serializedObject.getType()).thenReturn(new SimpleSerializedType("someObjectType",
                                                                             "10"));
        return serializedObject;
    }

    private static class Foo {

        private final String name;
        private final Bar bar;

        Foo(String name, Bar bar) {
            this.name = name;
            this.bar = bar;
        }

        @Override
        public String toString() {
            return "Foo{" +
                    "name='" + name + '\'' +
                    ", bar=" + bar +
                    '}';
        }
    }

    private static class Bar {

        private int count;

        Bar(int count) {
            this.count = count;
        }

        @Override
        public String toString() {
            return "Bar{" +
                    "count=" + count +
                    '}';
        }
    }
}