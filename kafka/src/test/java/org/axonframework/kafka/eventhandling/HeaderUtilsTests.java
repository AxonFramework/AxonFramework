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

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.axonframework.messaging.Headers;
import org.hamcrest.CoreMatchers;
import org.junit.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

/**
 * Tests for {@link HeaderUtils}
 *
 * @author Nakul Mishra
 */
public class HeaderUtilsTests {

    @Test
    public void testConvertingBytesToString_Ascii() {
        assertThat(HeaderUtils.asString("FFlETYeKU3H5QRqw8cjxxpwSCOg4IPqZwMmPCmPoozi9ryN8tG".getBytes()),
                   is("FFlETYeKU3H5QRqw8cjxxpwSCOg4IPqZwMmPCmPoozi9ryN8tG"));
    }

    @Test
    public void testConvertingBytesToString_UTF8() {
        assertThat(HeaderUtils.asString("Şơм℮ śẩмρŀę ÅŚÇÍỈ-ťęҳť".getBytes()), is("Şơм℮ śẩмρŀę ÅŚÇÍỈ-ťęҳť"));
    }

    @Test
    public void testConvertingBytesToString_null() {
        assertThat(HeaderUtils.asString(null), CoreMatchers.nullValue());
    }

    @Test
    public void testConvertingLongToBytes_Positive() {
        assertThat(HeaderUtils.toBytes(1_234_567_89_0L), is(bytes(1_234_567_89_0L)));
    }

    @Test
    public void testConvertingLongToBytes_Zero() {
        assertThat(HeaderUtils.toBytes(0), is(bytes(0)));
    }

    @Test
    public void testConvertingLongToBytes_Negative() {
        assertThat(HeaderUtils.toBytes(-1_111_111_111_222_54321L), is(bytes(-1_111_111_111_222_54321L)));
    }

    @Test
    public void testConvertingBytesToLong_Positive() {
        assertThat(HeaderUtils.asLong(bytes(-4_891_00_921_388_62621L)), is(-4_891_00_921_388_62621L));
    }

    @Test
    public void testConvertingBytesToLong_Zero() {
        assertThat(HeaderUtils.asLong(bytes(0)), is(0L));
    }

    @Test
    public void testConvertingBytesToLong_Negative() {
        assertThat(HeaderUtils.asLong(bytes(-4_891_00_921_388_62621L)), is(-4_891_00_921_388_62621L));
    }

    @Test
    public void testAppendingHeader() {
        RecordHeaders parent = emptyHeader();
        HeaderUtils.addBytes(parent, "foo", "bar");
        assertThat(parent.toArray().length, is(1));
    }

    @Test
    public void testAppendingHeader_String() {
        RecordHeaders parent = emptyHeader();
        String expectedKey = "someKey";
        String expectedValue = "someValue";
        HeaderUtils.addBytes(parent, expectedKey, expectedValue);
        assertThat(parent.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(parent.lastHeader(expectedKey).value(), is(expectedValue.getBytes()));
    }

    @Test
    public void testAppendingHeader_Instant() {
        RecordHeaders parent = emptyHeader();
        String expectedKey = "baz";
        Instant expectedValue = Instant.now();
        HeaderUtils.addBytes(parent, expectedKey, expectedValue);
        assertThat(parent.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(parent.lastHeader(expectedKey).value(), is(bytes(expectedValue.toEpochMilli())));
    }

    @Test
    public void testAppendingHeader_Long() {
        RecordHeaders parent = emptyHeader();
        String expectedKey = "foobar";
        long expectedValue = 100L;
        HeaderUtils.addBytes(parent, expectedKey, expectedValue);
        assertThat(parent.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(parent.lastHeader(expectedKey).value(), is(bytes(expectedValue)));
    }

    @Test
    public void testAppendingHeader_null() {
        RecordHeaders parent = emptyHeader();
        String expectedKey = "baz";
        HeaderUtils.addBytes(parent, expectedKey, null);
        assertThat(parent.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(parent.lastHeader(expectedKey).value(), is(nullValue()));
    }

    @Test
    public void testAppendingHeader_CustomObject() {
        RecordHeaders parent = emptyHeader();
        String expectedKey = "object";
        Foo expectedValue = new Foo("someName", new Bar(100));
        HeaderUtils.addBytes(parent, expectedKey, expectedValue);
        assertThat(parent.lastHeader(expectedKey).key(), is(expectedKey));
        assertThat(parent.lastHeader(expectedKey).value(), is(expectedValue.toString().getBytes()));
    }

    @Test
    public void keys() {
        RecordHeaders parent = emptyHeader();
        HeaderUtils.addBytes(parent, "a", "someValue");
        HeaderUtils.addBytes(parent, "b", "someValue");
        HeaderUtils.addBytes(parent, "c", "someValue");
        assertThat(keys(parent), is(HeaderUtils.keys(parent)));
    }

    @Test
    public void testExtractingAxonMetadata() {
        RecordHeaders parent = emptyHeader();
        String key = Headers.MESSAGE_METADATA + "-" + "headerKey";
        String value = "abc";
        Map<String, Object> expectedValue = new HashMap<String, Object>() {{
            put("headerKey", value);
        }};
        HeaderUtils.addBytes(parent, key, value);
        assertThat(HeaderUtils.extractAxonMetadata(parent), is(expectedValue));
    }

    private byte[] bytes(long l) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(l);
        return buffer.array();
    }

    private Set<String> keys(RecordHeaders parent) {
        Set<String> actual = new HashSet<>();
        parent.forEach(header -> actual.add(header.key()));
        return actual;
    }

    private RecordHeaders emptyHeader() {
        return new RecordHeaders();
    }

    private class Foo {
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

    private class Bar {

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