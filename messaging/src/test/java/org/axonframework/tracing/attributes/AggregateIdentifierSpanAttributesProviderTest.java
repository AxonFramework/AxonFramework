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

import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.tracing.SpanAttributesProvider;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AggregateIdentifierSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new AggregateIdentifierSpanAttributesProvider();

    @Test
    void domainEventMessage() {
        Message<?> message = new GenericDomainEventMessage<>(
                "MyType", "1729872981", 1, new QualifiedName("test", "event", "0.0.1"), "payload"
        );

        Map<String, String> map = provider.provideForMessage(message);
        assertEquals(1, map.size());
        assertEquals("1729872981", map.get("axon_aggregate_identifier"));
    }

    @Test
    void genericEventMessage() {
        Message<?> message = new GenericEventMessage<>(new QualifiedName("test", "event", "0.0.1"), "payload");

        Map<String, String> map = provider.provideForMessage(message);
        assertEquals(0, map.size());
    }
}
