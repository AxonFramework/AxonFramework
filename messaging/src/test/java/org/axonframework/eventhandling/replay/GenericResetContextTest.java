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

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericResetContext}.
 *
 * @author Steven van Beelen
 */
class GenericResetContextTest {

    private static final QualifiedName TEST_NAME = new QualifiedName("test", "reset", "0.0.1");
    private static final Object TEST_PAYLOAD = new Object();

    @Test
    void constructor() {
        ResetContext<Object> messageOne = new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        ResetContext<Object> messageTwo = new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD, metaDataMap);
        MetaData metaData = MetaData.from(metaDataMap);
        ResetContext<Object> messageThree = new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD, metaData);

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
    void withMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.withMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, messageOne.getMetaData().size());
        assertEquals(1, messageTwo.getMetaData().size());
    }

    @Test
    void andMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD, metaData);

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
        assertEquals("GenericResetContext", new GenericResetContext<>(TEST_NAME, TEST_PAYLOAD).describeType());
    }
}
