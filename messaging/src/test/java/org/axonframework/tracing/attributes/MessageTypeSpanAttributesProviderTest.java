/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.tracing.SpanAttributesProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

class MessageTypeSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new MessageTypeSpanAttributesProvider();

    @Test
    void correctTypeForQueryMessage() {
        Message<?> genericQueryMessage = new GenericQueryMessage<>(
                dottedName("test.query"), "myQueryName", "MyQuery", instanceOf(String.class)
        );
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("GenericQueryMessage", map.get("axon_message_type"));
    }

    @Test
    void correctTypeForCommandMessage() {
        Message<?> genericQueryMessage = new GenericCommandMessage<>(dottedName("test.command"), "payload");
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("GenericCommandMessage", map.get("axon_message_type"));
    }
}
