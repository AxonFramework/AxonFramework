/*
 * Copyright (c) 2010-2018. Axon Framework
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
     * Indicates whether the ResultMessage represents unsuccessful execution.
     *
     * @return {@code true} if execution was unsuccessful, {@code false} otherwise
     */
    boolean isExceptional();

    /**
     * Returns the Exception in case of exceptional result message or an empty {@link Optional} in case of successful
     * execution.
     *
     * @return an {@link Optional} containing exception result or an empty Optional in case of a successful execution
     */
    Optional<Throwable> optionalExceptionResult();

    /**
     * Returns the exception result. This method is to be called if {@link #isExceptional()} returns {@code true}.
     *
     * @return a {@link Throwable} defining the exception result
     *
     * @throws IllegalStateException if this ResultMessage is not exceptional
     */
    default Throwable exceptionResult() throws IllegalStateException {
        return optionalExceptionResult().orElseThrow(IllegalStateException::new);
    }

    /**
     * Serializes the exception result. Will create a {@link RemoteExceptionDescription} from the {@link Optional}
     * exception in this ResultMessage instead of serializing the original exception.
     *
     * @param serializer             the {@link Serializer} used to serialize the exception
     * @param expectedRepresentation a {@link Class} representing the expected format
     * @param <T>                    the generic type representing the expected format
     * @return the serialized exception as a {@link SerializedObject}
     */
    default <T> SerializedObject<T> serializeExceptionResult(Serializer serializer, Class<T> expectedRepresentation) {
        return serializer.serialize(
                optionalExceptionResult().map(RemoteExceptionDescription::describing).orElse(null),
                expectedRepresentation
        );
    }

    @Override
    ResultMessage<R> withMetaData(Map<String, ?> metaData);

    @Override
    ResultMessage<R> andMetaData(Map<String, ?> metaData);
}
