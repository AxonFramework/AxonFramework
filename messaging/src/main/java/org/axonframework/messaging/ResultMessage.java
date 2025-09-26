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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link Message} that represents a result of handling some form of request message.
 *
 * @author Milan Savic
 * @since 4.0.0
 */
public interface ResultMessage extends Message {

    /**
     * Indicates whether the ResultMessage represents unsuccessful execution.
     *
     * @return {@code true} if execution was unsuccessful, {@code false} otherwise
     * @deprecated Exceptional results will be reported as an exception or using a failed MessageStream
     */
    @Deprecated
    boolean isExceptional();

    /**
     * Returns the Exception in case of exceptional result message or an empty {@link Optional} in case of successful
     * execution.
     *
     * @return an {@link Optional} containing exception result or an empty Optional in case of a successful execution
     * @deprecated Exceptional results will be reported as an exception or using a failed MessageStream
     */
    @Deprecated
    Optional<Throwable> optionalExceptionResult();

    /**
     * Returns the exception result. This method is to be called if {@link #isExceptional()} returns {@code true}.
     *
     * @return a {@link Throwable} defining the exception result
     * @throws IllegalStateException if this ResultMessage is not exceptional
     * @deprecated Exceptional results will be reported as an exception or using a failed MessageStream
     */
    @Deprecated
    default Throwable exceptionResult() throws IllegalStateException {
        return optionalExceptionResult().orElseThrow(IllegalStateException::new);
    }

    /**
     * If the this message contains an exception result, returns the details provided in the exception, if available. If
     * this message does not carry an exception result, or the exception result doesn't provide any application-specific
     * details, an empty optional is returned.
     *
     * @param <D> The type of application-specific details expected
     * @return an optional containing application-specific error details, if present
     * @deprecated Exceptional results will be reported as an exception or using a failed MessageStream
     */
    @Deprecated
    default <D> Optional<D> exceptionDetails() {
        return optionalExceptionResult().flatMap(HandlerExecutionException::resolveDetails);
    }

    @Override
    @Nonnull
    ResultMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    ResultMessage andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default ResultMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default ResultMessage withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    ResultMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
