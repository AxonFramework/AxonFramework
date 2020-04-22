/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.serialization.gson;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.axonframework.serialization.gson.JsonElementToByteArrayConverter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class JsonNodeToByteArrayConverterTest {

    private final JsonElementToByteArrayConverter testSubject = new JsonElementToByteArrayConverter(new Gson());

    @Test
    void testConvertNodeToBytes() {
        JsonObject elem = new JsonObject();
        elem.add("someKey", new JsonPrimitive("someValue"));
        elem.add("someOther", new JsonPrimitive(true));

        final byte[] expected = "{\"someKey\":\"someValue\",\"someOther\":true}".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected, testSubject.convert(elem));
    }
}
