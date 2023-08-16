/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.messaging.Message;

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Represents a Message initiating the reset of an Event Handling Component. A payload of {@code T} can be provided to
 * support the reset operation handling this message.
 *
 * @param <T> the type of payload contained in the message
 * @author Steven van Beelen
 * @since 4.4
 */
public interface ResetContext<T> extends Message<T> {

    /**
     * Returns a copy of this {@link ResetContext} with the given {@code metaData}. The payload remains unchanged.
     * <p/>
     * While the implementation returned may be different than the implementation of {@code this}, implementations must
     * take special care in returning the same type of Message (e.g. EventMessage, DomainEventMessage) to prevent errors
     * further downstream.
     *
     * @param metaData the new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    ResetContext<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this {@link ResetContext} with it MetaData merged with the given {@code metaData}. The payload
     * remains unchanged.
     *
     * @param metaData the MetaData to merge with
     * @return a copy of this message with the given MetaData
     */
    @Override
    ResetContext<T> andMetaData(@Nonnull Map<String, ?> metaData);
}
