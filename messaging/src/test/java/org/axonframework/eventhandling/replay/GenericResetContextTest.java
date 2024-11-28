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

package org.axonframework.eventhandling.replay;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedNameUtils;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.eventhandling.replay.GenericResetContext.asResetContext;
import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericResetContext}.
 *
 * @author Steven van Beelen
 */
class GenericResetContextTest {

    private static final Object TEST_PAYLOAD = new Object();

    @Test
    void constructor() {
        ResetContext<Object> messageOne = asResetContext(TEST_PAYLOAD);
        ResetContext<Object> messageTwo = asResetContext(new GenericMessage<>(QualifiedNameUtils.fromDottedName("test.event"), TEST_PAYLOAD));
        ResetContext<Object> messageThree = asResetContext(new GenericResetContext<>(TEST_PAYLOAD));

        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        ResetContext<Object> messageFour = new GenericResetContext<>(TEST_PAYLOAD, metaDataMap);
        MetaData metaData = MetaData.from(metaDataMap);
        ResetContext<Object> messageFive = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        assertSame(MetaData.emptyInstance(), messageOne.getMetaData());
        assertEquals(Object.class, messageOne.getPayload().getClass());
        assertEquals(Object.class, messageOne.getPayloadType());

        assertSame(MetaData.emptyInstance(), messageTwo.getMetaData());
        assertEquals(Object.class, messageTwo.getPayload().getClass());
        assertEquals(Object.class, messageTwo.getPayloadType());

        assertSame(MetaData.emptyInstance(), messageThree.getMetaData());
        assertEquals(Object.class, messageThree.getPayload().getClass());
        assertEquals(Object.class, messageThree.getPayloadType());

        assertNotSame(metaDataMap, messageFour.getMetaData());
        assertEquals(metaDataMap, messageFour.getMetaData());
        assertEquals(Object.class, messageFour.getPayload().getClass());
        assertEquals(Object.class, messageFour.getPayloadType());

        assertSame(metaData, messageFive.getMetaData());
        assertEquals(Object.class, messageFive.getPayload().getClass());
        assertEquals(Object.class, messageFive.getPayloadType());

        assertNotEquals(messageOne.getIdentifier(), messageFour.getIdentifier());
        assertNotEquals(messageFour.getIdentifier(), messageFive.getIdentifier());
        assertNotEquals(messageFive.getIdentifier(), messageOne.getIdentifier());
    }

    @Test
    void withMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.withMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, messageOne.getMetaData().size());
        assertEquals(1, messageTwo.getMetaData().size());
    }

    @Test
    void andMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.andMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, messageOne.getMetaData().size());
        assertEquals("value", messageOne.getMetaData().get("key"));
        assertEquals(1, messageTwo.getMetaData().size());
        assertEquals("otherValue", messageTwo.getMetaData().get("key"));
    }

    @Test
    void describeType() {
        assertEquals("GenericResetContext", new GenericResetContext<>(TEST_PAYLOAD).describeType());
    }
}
