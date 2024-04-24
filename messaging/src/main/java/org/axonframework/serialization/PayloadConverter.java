/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.serialization;

import org.axonframework.messaging.Message;

import java.lang.reflect.Type;

/**
 * A mapper function that can change the representation of a Message's payload. Generally, conversion only involves the
 * technical representation of a message's payload, such as converting it from bytes to a JSON object.
 * <p>
 * As the conversion is considered a change in the payload's (technical) representation, the returned message is
 * considered semantically equal to the message that was used as input to the conversion.
 */
public interface PayloadConverter {

    /**
     * Convert the payload of given {@code message} to given {@code targetPayloadType}.
     *
     * @param message           The message to convert the payload for
     * @param targetPayloadType The type to convert the payload to
     * @param <T>               The type of message to convert
     * @param <R>               The type of message to convert into
     * @return the converted message
     */
    <T extends Message<?>, R extends Message<?>> R convertPayload(T message, Type targetPayloadType);

    /**
     * Convert the payload of given {@code message} to given {@code targetPayloadType}.
     * <p>
     * This method is equal to the {@link #convertPayload(Message, Type)} method, but give additional compiler hints of
     * the expected type of message.
     *
     * @param message           The message to convert the payload for
     * @param targetPayloadType The type to convert the payload to
     * @param <P>               THe type to convert the message's payload into
     * @param <T>               The type of message to convert
     * @param <R>               The type of message to convert into
     * @return the converted message
     */
    default <P, T extends Message<?>, R extends Message<P>> R convertPayload(T message, Class<P> targetPayloadType) {
        return convertPayload(message, (Type) targetPayloadType);
    }
}
