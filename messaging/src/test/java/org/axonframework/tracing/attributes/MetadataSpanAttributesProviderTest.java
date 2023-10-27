/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.tracing.attributes;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.SpanAttributesProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.*;

class MetadataSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new MetadataSpanAttributesProvider();

    @Test
    void addsAllMetadata() {
        Message<?> message = new GenericEventMessage<>("MyEvent")
                .andMetaData(singletonMap("myKeyOne", "valueOne"))
                .andMetaData(singletonMap("myNumberKey", 2))
                .andMetaData(singletonMap("someOtherKey_2", "someValue"));
        Map<String, String> map = provider.provideForMessage(message);
        assertEquals(3, map.size());
        assertEquals("valueOne", map.get("axon_metadata_myKeyOne"));
        assertEquals("2", map.get("axon_metadata_myNumberKey"));
        assertEquals("someValue", map.get("axon_metadata_someOtherKey_2"));
    }
}
