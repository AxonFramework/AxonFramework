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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageStream.MessageEntry;

import java.util.function.Function;

/**
 * Simple implementation of the {@link MessageEntry} that only contains a single {@link Message} implementation of type
 * {@code M}
 *
 * @param <M>     The type of {@link Message} contained in this {@link MessageEntry} implementation.
 * @param message The {@link Message} of type {@code M} contained in this {@link MessageEntry}.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record SimpleMessageEntry<M extends Message<?>>(M message) implements MessageEntry<M> {

    @Override
    public <RM extends Message<?>> MessageEntry<RM> map(@Nonnull Function<M, RM> mapper) {
        return new SimpleMessageEntry<>(mapper.apply(message()));
    }
}
