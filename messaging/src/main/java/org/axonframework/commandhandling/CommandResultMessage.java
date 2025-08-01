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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.ResultMessage;

import java.util.Map;
import java.util.function.Function;

/**
 * A {@link ResultMessage} that represents a result from handling a {@link CommandMessage}.
 *
 * @param <R> The type of {@link #payload() result} contained in this {@link CommandResultMessage}.
 * @author Milan Savic
 * @since 4.0.0
 */
public interface CommandResultMessage<R> extends ResultMessage<R> {

    @Override
    CommandResultMessage<R> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    CommandResultMessage<R> andMetaData(@Nonnull Map<String, String> metaData);

    // TODO - @Override from ResultMessage and Message
    <T> CommandResultMessage<T> withConvertedPayload(@Nonnull Function<R, T> conversion);
}
