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

package org.axonframework.deadline;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericDeadlineMessage}.
 *
 * @author Steven van Beelen
 */
class GenericDeadlineMessageTest extends MessageTestSuite<DeadlineMessage<?>> {

    @Override
    protected <P> DeadlineMessage<?> buildMessage(@Nullable P payload) {
        return new GenericDeadlineMessage<>("deadlineName",
                                            new MessageType(ObjectUtils.nullSafeTypeOf(payload)),
                                            payload);
    }

    @Override
    protected void validateMessageSpecifics(@Nonnull DeadlineMessage<?> actual, @Nonnull DeadlineMessage<?> result) {
        assertThat(actual.getDeadlineName()).isEqualTo(result.getDeadlineName());
        assertThat(actual.timestamp()).isEqualTo(result.timestamp());
    }
}