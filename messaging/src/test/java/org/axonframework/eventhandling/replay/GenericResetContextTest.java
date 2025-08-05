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

package org.axonframework.eventhandling.replay;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericResetContext}.
 *
 * @author Steven van Beelen
 */
class GenericResetContextTest extends MessageTestSuite<ResetContext<?>> {

    @Override
    protected ResetContext<?> buildDefaultMessage() {
        return new GenericResetContext<>(new GenericMessage<>(
                TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_META_DATA
        ));
    }

    @Override
    protected <P> ResetContext<?> buildMessage(@Nullable P payload) {
        return new GenericResetContext<>(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Test
    void withMetaData() {
        MetaData metaData = MetaData.from(Collections.singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_TYPE, TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.withMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.withMetaData(MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(0, messageOne.metaData().size());
        assertEquals(1, messageTwo.metaData().size());
    }

    @Test
    void andMetaData() {
        MetaData metaData = MetaData.from(Collections.singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_TYPE, TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.andMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.andMetaData(MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(1, messageOne.metaData().size());
        assertEquals("value", messageOne.metaData().get("key"));
        assertEquals(1, messageTwo.metaData().size());
        assertEquals("otherValue", messageTwo.metaData().get("key"));
    }

    @Test
    void describeType() {
        assertEquals("GenericResetContext", new GenericResetContext<>(TEST_TYPE, TEST_PAYLOAD).describeType());
    }
}
