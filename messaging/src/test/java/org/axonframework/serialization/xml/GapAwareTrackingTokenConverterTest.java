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

package org.axonframework.serialization.xml;

import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.serialization.GapAwareTrackingTokenConverter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GapAwareTrackingTokenConverter}. Does so against the new format the
 * {@code GapAwareTrackingTokenConverter} constructs and the previous Reflective format constructed by XStream
 * pre-JDK17.
 *
 * @author Steven van Beelen
 */
class GapAwareTrackingTokenConverterTest {

    private static final GapAwareTrackingToken TEST_TOKEN =
            GapAwareTrackingToken.newInstance(Long.MAX_VALUE, asList(0L, 1L));
    public static final String TOKEN_CLASS_NAME = "org.axonframework.eventhandling.GapAwareTrackingToken";
    private static final String REFLECTIVE_XML_FORMAT =
            "<org.axonframework.eventhandling.GapAwareTrackingToken>"
                    + "<index>9223372036854775807</index>"
                    + "<gaps class=\"java.util.concurrent.ConcurrentSkipListSet\">"
                    + "<m class=\"java.util.concurrent.ConcurrentSkipListMap\" serialization=\"custom\">"
                    + "<unserializable-parents/>"
                    + "<java.util.concurrent.ConcurrentSkipListMap>"
                    + "<default/>"
                    + "<long>0</long>"
                    + "<boolean>true</boolean>"
                    + "<long>1</long>"
                    + "<boolean>true</boolean>"
                    + "<null/>"
                    + "</java.util.concurrent.ConcurrentSkipListMap>"
                    + "</m>"
                    + "</gaps>"
                    + "</org.axonframework.eventhandling.GapAwareTrackingToken>";
    private static final String CUSTOM_CONVERTER_XML_FORMAT =
            "<org.axonframework.eventhandling.GapAwareTrackingToken>"
                    + "<index>9223372036854775807</index>"
                    + "<gaps>"
                    + "<long>0</long>"
                    + "<long>1</long>"
                    + "</gaps>"
                    + "</org.axonframework.eventhandling.GapAwareTrackingToken>";

    private final XStreamSerializer serializer = (XStreamSerializer) TestSerializer.XSTREAM.getSerializer();

    @Test
    void canDeserializeReflectiveFormat() {
        SerializedObject<String> reflectivelySerializedToken =
                new SimpleSerializedObject<>(REFLECTIVE_XML_FORMAT, String.class, TOKEN_CLASS_NAME, null);

        GapAwareTrackingToken result = serializer.deserialize(reflectivelySerializedToken);
        assertEquals(TEST_TOKEN, result);
    }

    @Test
    void canDeserializeCustomConverterFormat() {
        SerializedObject<String> reflectivelySerializedToken =
                new SimpleSerializedObject<>(CUSTOM_CONVERTER_XML_FORMAT, String.class, TOKEN_CLASS_NAME, null);

        GapAwareTrackingToken result = serializer.deserialize(reflectivelySerializedToken);
        assertEquals(TEST_TOKEN, result);
    }
}
