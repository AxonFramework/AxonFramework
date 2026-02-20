/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Implementation of the {@link MessageStream} that ignores all {@link Entry entries} of the {@code delegate} stream and
 * returns an empty stream.
 * <p>
 * This allows users to define a {@code MessageStream} of any type and force it to a
 * {@link MessageStream.Empty} stream instance, effectively ignoring the results while
 * maintaining the processing of the stream.
 *
 * @param <M> The type of {@link Message} from the delegate stream that will be ignored.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class IgnoredEntriesMessageStream<M extends Message>
        extends DelegatingMessageStream<M, Message>
        implements MessageStream.Empty<Message> {

    private final Empty<Message> empty;

    /**
     * Constructs the IgnoreMessageStream with given {@code delegate} to receive and ignore entries from.
     *
     * @param delegate The instance to delegate calls to.
     */
    IgnoredEntriesMessageStream(@Nonnull MessageStream<M> delegate) {
        super(delegate);
        this.empty = MessageStream.empty();
    }

    @Override
    public Optional<Entry<Message>> next() {
        return delegate().next().flatMap(r -> Optional.empty());
    }

    @Override
    public Optional<Entry<Message>> peek() {
        return Optional.empty();
    }
//
//    @Override
//    public Empty<Message> first() {
//        return empty.first();
//    }
//
//    @Override
//    public <RM extends Message> Empty<RM> map(@NonNull Function<Entry<Message>, Entry<RM>> mapper) {
//        return empty.map(mapper);
//    }
//
//    @Override
//    public <RM extends Message> Empty<RM> mapMessage(@NonNull Function<Message, RM> mapper) {
//        return empty.mapMessage(mapper);
//    }
//
    @Override
    public Empty<Message> onNext(@NonNull Consumer<Entry<Message>> onNext) {
        return empty.onNext(onNext);
    }
//
//    @Override
//    public MessageStream<Message> concatWith(@NonNull MessageStream<Message> other) {
//        return empty.concatWith(other);
//    }
//
//    @Override
//    public Empty<Message> onComplete(@NonNull Runnable completeHandler) {
//        return empty.onComplete(completeHandler);
//    }
//
//    @Override
//    public <T extends Message> Empty<T> cast() {
//        return empty.cast();
//    }
    // TODO: error propagation, when callback on
}