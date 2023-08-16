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

package org.axonframework.common.stream;

import org.axonframework.eventhandling.TrackingEventStream;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.stream.StreamSupport.stream;

/**
 * Utility class for working with Streams.
 */
public abstract class StreamUtils {

    private StreamUtils() {
    }

    /**
     * Convert the given {@code messageStream} to a regular java {@link Stream}. Note that the returned
     * stream will block during iteration if the end of the stream is reached so take heed of this in production code.
     *
     * Closing this {@code Stream} will close the underling {@code messageStream} as well.
     *
     * @param messageStream the input {@link TrackingEventStream}
     * @return the output {@link Stream} after conversion
     * @param <M> The type of entry contained in the stream
     */
    public static <M> Stream<M> asStream(BlockingStream<M> messageStream) {
        Spliterator<M> spliterator =
                new Spliterators.AbstractSpliterator<M>(Long.MAX_VALUE, DISTINCT | NONNULL | ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super M> action) {
                        try {
                            action.accept(messageStream.nextAvailable());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        return true;
                    }
                };
        return stream(spliterator, false).onClose(messageStream::close);
    }
}
