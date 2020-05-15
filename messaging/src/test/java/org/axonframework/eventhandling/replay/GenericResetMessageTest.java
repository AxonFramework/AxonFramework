/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.eventhandling.replay.GenericResetMessage.asResetMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericResetMessage}.
 *
 * @author Steven van Beelen
 */
class GenericResetMessageTest {

    private static final Object TEST_PAYLOAD = new Object();

    @Test
    void testConstructor() {
        ResetMessage<Object> messageOne = asResetMessage(TEST_PAYLOAD);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        ResetMessage<Object> messageTwo = new GenericResetMessage<>(TEST_PAYLOAD, metaDataMap);
        MetaData metaData = MetaData.from(metaDataMap);
        ResetMessage<Object> messageThree = new GenericResetMessage<>(TEST_PAYLOAD, metaData);

        assertSame(MetaData.emptyInstance(), messageOne.getMetaData());
        assertEquals(Object.class, messageOne.getPayload().getClass());
        assertEquals(Object.class, messageOne.getPayloadType());

        assertNotSame(metaDataMap, messageTwo.getMetaData());
        assertEquals(metaDataMap, messageTwo.getMetaData());
        assertEquals(Object.class, messageTwo.getPayload().getClass());
        assertEquals(Object.class, messageTwo.getPayloadType());

        assertSame(metaData, messageThree.getMetaData());
        assertEquals(Object.class, messageThree.getPayload().getClass());
        assertEquals(Object.class, messageThree.getPayloadType());

        assertNotEquals(messageOne.getIdentifier(), messageTwo.getIdentifier());
        assertNotEquals(messageTwo.getIdentifier(), messageThree.getIdentifier());
        assertNotEquals(messageThree.getIdentifier(), messageOne.getIdentifier());
    }

    @Test
    void testWithMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetMessage<Object> startMessage = new GenericResetMessage<>(TEST_PAYLOAD, metaData);

        ResetMessage<Object> messageOne =
                startMessage.withMetaData(MetaData.emptyInstance());
        ResetMessage<Object> messageTwo =
                startMessage.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, messageOne.getMetaData().size());
        assertEquals(1, messageTwo.getMetaData().size());
    }

    @Test
    void testAndMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetMessage<Object> startMessage = new GenericResetMessage<>(TEST_PAYLOAD, metaData);

        ResetMessage<Object> messageOne =
                startMessage.andMetaData(MetaData.emptyInstance());
        ResetMessage<Object> messageTwo =
                startMessage.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, messageOne.getMetaData().size());
        assertEquals("value", messageOne.getMetaData().get("key"));
        assertEquals(1, messageTwo.getMetaData().size());
        assertEquals("otherValue", messageTwo.getMetaData().get("key"));
    }

    @Test
    void testDescribeType() {
        assertEquals("GenericResetMessage", new GenericResetMessage<>(TEST_PAYLOAD).describeType());
    }
}