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

package org.axonframework.tracing.attributes;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.tracing.SpanAttributesProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageNameSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new MessageNameSpanAttributesProvider();

    @Test
    void extractsForEvent() {
        EventMessage event = EventTestUtils.asEventMessage("Some event");
        Map<String, String> map = provider.provideForMessage(event);
        assertEquals(1, map.size());
        assertEquals("java.lang.String#0.0.1", map.get("axon_message_name"));
    }

    @Test
    void extractsForQuery() {
        Message genericQueryMessage = new GenericQueryMessage(new MessageType("query"),
                                                              "MyQuery",
                                                              new MessageType(String.class));
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("query#0.0.1", map.get("axon_message_name"));
    }

    @Test
    void extractsForCommand() {
        Message genericQueryMessage = new GenericCommandMessage(new GenericCommandMessage(
                new MessageType("MyAwesomeCommand"), "payload"
        ));
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("MyAwesomeCommand#0.0.1", map.get("axon_message_name"));
    }
}
