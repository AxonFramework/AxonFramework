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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.reactivestreams.Publisher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericStreamingQueryMessage}.
 *
 * @author Steven van Beelen
 */
class GenericStreamingQueryMessageTest extends MessageTestSuite<StreamingQueryMessage> {

    private static final ResponseType<Publisher<String>> TEST_RESPONSE_TYPE = ResponseTypes.publisherOf(String.class);

    @Override
    protected StreamingQueryMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericStreamingQueryMessage(delegate, String.class);
    }

    @Override
    protected <P> StreamingQueryMessage buildMessage(@Nullable P payload) {
        return new GenericStreamingQueryMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                                payload,
                                                String.class);
    }

    @Override
    protected void validateDefaultMessage(@Nonnull StreamingQueryMessage result) {
        assertThat(TEST_RESPONSE_TYPE).isEqualTo(result.responseType());
    }

    @Override
    protected void validateMessageSpecifics(@Nonnull StreamingQueryMessage actual,
                                            @Nonnull StreamingQueryMessage result) {
        assertThat(actual.responseType()).isEqualTo(result.responseType());
    }
}