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
import org.axonframework.messaging.Message;
import org.axonframework.serialization.Converter;

import java.util.Map;

/**
 * A {@link Message} carrying a command as its payload.
 * <p>
 * These messages carry an intention to change application state.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link CommandMessage}.
 * @author Allard Buijze
 * @since 2.0.0
 */
public interface CommandMessage<P> extends Message<P> {

    @Override
    CommandMessage<P> withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    CommandMessage<P> andMetaData(@Nonnull Map<String, String> metaData);

    @Override
    <T> CommandMessage<T> withConvertedPayload(@Nonnull Class<T> type, @Nonnull Converter converter);
}
