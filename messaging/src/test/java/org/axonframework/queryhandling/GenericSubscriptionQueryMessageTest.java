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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericSubscriptionQueryMessage}.
 *
 * @author Steven van Beelen
 */
class GenericSubscriptionQueryMessageTest extends MessageTestSuite<SubscriptionQueryMessage> {

    private static final ResponseType<String> TEST_RESPONSE_TYPE = ResponseTypes.instanceOf(String.class);
    private static final ResponseType<String> TEST_UPDATES_TYPE = ResponseTypes.instanceOf(String.class);

    @Override
    protected SubscriptionQueryMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericSubscriptionQueryMessage(delegate,
                                                   TEST_RESPONSE_TYPE,
                                                   TEST_UPDATES_TYPE);
    }

    @Override
    protected <P> SubscriptionQueryMessage buildMessage(@Nullable P payload) {
        return new GenericSubscriptionQueryMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                                   payload,
                                                   TEST_RESPONSE_TYPE,
                                                   TEST_UPDATES_TYPE);
    }

    @Override
    protected void validateDefaultMessage(@Nonnull SubscriptionQueryMessage result) {
        assertThat(TEST_RESPONSE_TYPE).isEqualTo(result.responseType());
        assertThat(TEST_UPDATES_TYPE).isEqualTo(result.updatesResponseType());
    }

    @Override
    protected void validateMessageSpecifics(@Nonnull SubscriptionQueryMessage actual,
                                            @Nonnull SubscriptionQueryMessage result) {
        assertThat(actual.responseType()).isEqualTo(result.responseType());
        assertThat(actual.updatesResponseType()).isEqualTo(result.updatesResponseType());
    }
}