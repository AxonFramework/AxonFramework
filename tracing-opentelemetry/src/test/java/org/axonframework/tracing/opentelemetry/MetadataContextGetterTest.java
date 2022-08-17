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

package org.axonframework.tracing.opentelemetry;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class MetadataContextGetterTest {

    private final EventMessage<Object> message = GenericEventMessage
            .asEventMessage("MyEvent")
            .andMetaData(Collections.singletonMap("myKeyOne", "myValueTwo"))
            .andMetaData(Collections.singletonMap("MyKeyTwo", 2));

    @Test
    void testShouldReceiveMetadataKeysFromMessage() {
        List<String> keys = StreamSupport.stream(MetadataContextGetter.INSTANCE.keys(message).spliterator(), false)
                                         .collect(Collectors.toList());
        assertTrue(keys.contains("myKeyOne"));
        assertTrue(keys.contains("MyKeyTwo"));
        assertFalse(keys.contains("MyKeyThree"));
    }

    @Test
    void testShouldGetItemFromMessage() {
        assertEquals("myValueTwo", MetadataContextGetter.INSTANCE.get(message, "myKeyOne"));
    }

    @Test
    void testShouldGetNullFromNullMessage() {
        assertNull(MetadataContextGetter.INSTANCE.get(null, "myKeyOne"));
    }
}
