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

package org.axonframework.commandhandling;

import org.axonframework.messaging.Message;

import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Represents a Message carrying a command as its payload. These messages carry an intention to change application
 * state.
 *
 * @param <T> The type of payload contained in the message
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandMessage<T> extends Message<T> {

    /**
     * Returns the name of the command to execute. This is an indication of what should be done, using the payload as
     * parameter.
     *
     * @return the name of the command
     */
    String getCommandName();

    /**
     * Returns a copy of this CommandMessage with the given {@code metaData}. The payload remains unchanged.
     * <p/>
     * While the implementation returned may be different than the implementation of {@code this}, implementations must
     * take special care in returning the same type of Message (e.g. EventMessage, DomainEventMessage) to prevent errors
     * further downstream.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    CommandMessage<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this CommandMessage with it MetaData merged with the given {@code metaData}. The payload
     * remains unchanged.
     *
     * @param metaData The MetaData to merge with
     * @return a copy of this message with the given MetaData
     */
    @Override
    CommandMessage<T> andMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    <C> CommandMessage<C> withConvertedPayload(@Nonnull Function<T, C> conversion);
}
