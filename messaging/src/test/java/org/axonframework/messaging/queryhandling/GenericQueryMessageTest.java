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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericQueryMessage}.
 *
 * @author Steven van Beelen
 */
class GenericQueryMessageTest extends MessageTestSuite<QueryMessage> {

    @Override
    protected QueryMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericQueryMessage(delegate);
    }

    @Override
    protected <P> QueryMessage buildMessage(@Nullable P payload) {
        return new GenericQueryMessage(new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                       payload), 42);
    }

    @Override
    protected void validateDefaultMessage(@Nonnull QueryMessage result) {
        assertThat(result.priority()).isNotPresent();
    }

    @Override
    protected void validateMessageSpecifics(@Nonnull QueryMessage actual, @Nonnull QueryMessage result) {
        assertThat(result.priority()).hasValue(42);
    }
}