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

import java.util.Optional;

/**
 * Implementation of the {@link MessageStream} that ignores all {@link Entry entries} of the {@code delegate} stream and
 * returns an empty stream.
 * <p>
 * This allows users to define a {@code MessageStream} of any type and force it to a
 * {@link org.axonframework.messaging.MessageStream.Empty} stream instance, effectively ignoring the results while
 * maintaining the processing of the stream.
 *
 * @param <M> The type of {@link Message} from the delegate stream that will be ignored.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class IgnoredMessageResultsStream<M extends Message<?>>
        extends DelegatingMessageStream<M, Message<Void>>
        implements MessageStream.Empty<Message<Void>> {

    /**
     * Constructs the IgnoreMessageStream with given {@code delegate} to receive and ignore entries from.
     *
     * @param delegate The instance to delegate calls to.
     */
    public IgnoredMessageResultsStream(@Nonnull MessageStream<M> delegate) {
        super(delegate);
    }

    @Override
    public Optional<Entry<Message<Void>>> next() {
        return delegate().next().flatMap(r -> Optional.empty());
    }

}