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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

/**
 * Test class validating the {@link GenericQueryResponseMessage}.
 *
 * @author Steven van Beelen
 */
class GenericQueryResponseMessageTest extends MessageTestSuite<QueryResponseMessage> {

    @Override
    protected QueryResponseMessage buildDefaultMessage() {
        return new GenericQueryResponseMessage(new GenericMessage(
                TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA
        ));
    }

    @Override
    protected <P> QueryResponseMessage buildMessage(@Nullable P payload) {
        return new GenericQueryResponseMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }
}