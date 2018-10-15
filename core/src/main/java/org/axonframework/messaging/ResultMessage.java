/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Optional;

/**
 * Message that represents a result of handling some form of request message.
 *
 * @param <R> The type of payload contained in this Message
 * @author Milan Savic
 * @since 4.0
 */
public interface ResultMessage<R> extends Message<R> {

    /**
     * Indicates whether the Result Message represents unsuccessful execution.
     *
     * @return {@code true} if execution was unsuccessful, {@code false} otherwise
     */
    // TODO: 10/11/2018 remove default: https://github.com/AxonFramework/AxonFramework/issues/827
    default boolean isExceptional() {
        return Throwable.class.isAssignableFrom(getPayloadType());
    }

    /**
     * Tries to get exception result if present.
     *
     * @return an Optional containing exception result
     */
    // TODO: 10/11/2018 remove default: https://github.com/AxonFramework/AxonFramework/issues/827
    default Optional<Throwable> tryGetExceptionResult() {
        return isExceptional() ? Optional.ofNullable((Throwable) getPayload()) : Optional.empty();
    }

    /**
     * Gets exception result. This method is to be called if {@link #isExceptional()} returns {@code true}.
     *
     * @return exception result
     *
     * @throws IllegalStateException if Result Message is not ecxeptional
     */
    default Throwable getExceptionResult() throws IllegalStateException {
        return tryGetExceptionResult().orElseThrow(IllegalStateException::new);
    }

    /**
     * Serializes the exception result.
     *
     * @param serializer             the Serializer
     * @param expectedRepresentation the type representing the expected format
     * @param <T>                    the tyep representing the expected format
     * @return serialized object
     */
    default <T> SerializedObject<T> serializeExceptionResult(Serializer serializer, Class<T> expectedRepresentation) {
        return serializer.serialize(tryGetExceptionResult().orElse(null), expectedRepresentation);
    }

    @Override
    ResultMessage<R> withMetaData(Map<String, ?> metaData);

    @Override
    ResultMessage<R> andMetaData(Map<String, ?> metaData);
}
