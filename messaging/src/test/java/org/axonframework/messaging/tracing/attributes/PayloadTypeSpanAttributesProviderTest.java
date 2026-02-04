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

package org.axonframework.messaging.tracing.attributes;

import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.tracing.SpanAttributesProvider;
import org.axonframework.messaging.tracing.attributes.PayloadTypeSpanAttributesProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayloadTypeSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new PayloadTypeSpanAttributesProvider();

    @Test
    void stringPayload() {
        Message message = new GenericEventMessage(new MessageType("event"), "MyEvent");

        Map<String, String> map = provider.provideForMessage(message);
        assertEquals(1, map.size());
        assertEquals("java.lang.String", map.get("axon_payload_type"));
    }

    @Test
    void classPayload() {
        Message message = new GenericEventMessage(new MessageType("event"), new MyEvent());

        Map<String, String> map = provider.provideForMessage(message);
        assertEquals(1, map.size());
        assertEquals("org.axonframework.messaging.tracing.attributes.PayloadTypeSpanAttributesProviderTest$MyEvent",
                     map.get("axon_payload_type"));
    }

    private static class MyEvent {

    }
}
