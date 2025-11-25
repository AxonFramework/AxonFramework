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

package org.axonframework.messaging.core;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.junit.jupiter.api.*;

import static org.axonframework.messaging.core.GenericResultMessage.asResultMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link GenericResultMessage}.
 *
 * @author Milan Savic
 */
class GenericResultMessageTest extends MessageTestSuite<ResultMessage> {

    @Override
    protected ResultMessage buildDefaultMessage() {
        return new GenericResultMessage(new GenericMessage(
                TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA
        ));
    }

    @Override
    protected <P> ResultMessage buildMessage(@Nullable P payload) {
        return new GenericResultMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Test
    void exceptionalResult() {
        Throwable t = new Throwable("oops");
        ResultMessage resultMessage = asResultMessage(t);
        try {
            resultMessage.payload();
        } catch (IllegalPayloadAccessException ipae) {
            assertEquals(t, ipae.getCause());
        }
    }
}
