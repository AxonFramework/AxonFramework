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

package org.axonframework.queryhandling;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GenericSubscriptionQueryUpdateMessage}.
 *
 * @author Milan Savic
 */
class GenericSubscriptionQueryUpdateMessageTest extends MessageTestSuite {

    @Override
    protected <P, M extends Message<P>> M buildMessage(@Nullable P payload) {
        //noinspection unchecked
        return (M) new GenericSubscriptionQueryUpdateMessage<>(new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                                               payload);
    }

    @Test
    void messageCreation() {
        String payload = "payload";

        SubscriptionQueryUpdateMessage<String> result = new GenericSubscriptionQueryUpdateMessage<>(
                new MessageType("query"), payload, String.class
        );

        assertEquals(payload, result.payload());
    }

    @Test
    void messageCreationWithNullPayload() {
        String payload = null;

        SubscriptionQueryUpdateMessage<String> result = new GenericSubscriptionQueryUpdateMessage<>(
                new MessageType("query"), payload, String.class
        );

        assertNull(result.payload());
    }

    @Test
    void andMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>(new MessageType("query"), "payload", metaData)
        );

        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.andMetaData(newMetaData);

        assertEquals(original.payload(), result.payload());
        MetaData expectedMetaData = MetaData.from(metaData)
                                            .mergedWith(newMetaData);
        assertEquals(expectedMetaData, result.metaData());
    }

    @Test
    void withMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>(new MessageType("query"), "payload", metaData)
        );

        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.withMetaData(newMetaData);

        assertEquals(original.payload(), result.payload());
        assertEquals(newMetaData, result.metaData());
    }
}
