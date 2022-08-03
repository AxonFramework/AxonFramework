/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.eventhandling.EventMessage;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Represents a Message for a Deadline, specified by its deadline name and optionally containing a deadline payload.
 * Implementations of DeadlineMessage represent a fact (it's a specialization of EventMessage) that some deadline was
 * reached. The optional payload contains relevant data of the scheduled deadline.
 *
 * @param <T> The type of payload contained in this Message; may be {@link Void} if no payload was provided
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public interface DeadlineMessage<T> extends EventMessage<T> {

    /**
     * Retrieve a {@link String} representing the name of this DeadlineMessage.
     *
     * @return a {@link String} representing the name of this DeadlineMessage
     */
    String getDeadlineName();

    /**
     * Returns a copy of this DeadlineMessage with the given {@code metaData}. The payload remains unchanged.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    DeadlineMessage<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this DeadlineMessage with its MetaData merged with given {@code additionalMetaData}. The
     * payload remains unchanged.
     *
     * @param additionalMetaData The MetaData to merge into the DeadlineMessage
     * @return a copy of this message with added additional MetaData
     */
    @Override
    DeadlineMessage<T> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
