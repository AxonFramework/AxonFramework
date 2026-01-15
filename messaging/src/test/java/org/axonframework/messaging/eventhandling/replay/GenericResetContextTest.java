/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.replay.GenericResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericResetContext}.
 *
 * @author Steven van Beelen
 */
class GenericResetContextTest extends MessageTestSuite<ResetContext> {

    @Override
    protected ResetContext buildDefaultMessage() {
        return new GenericResetContext(new GenericMessage(
                TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA
        ));
    }

    @Override
    protected <P> ResetContext buildMessage(@Nullable P payload) {
        return new GenericResetContext(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Test
    void describeType() {
        assertEquals("GenericResetContext", new GenericResetContext(TEST_TYPE, TEST_PAYLOAD).describeType());
    }
}
