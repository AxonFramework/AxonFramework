/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.eventsourcing.eventstore.TrackingEventStream;

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

    /**
     * Convert the given {@code messageStream} to a regular java {@link Stream} of messages. Note that the returned
     * stream will block during iteration if the end of the stream is reached so take heed of this in production code.
     *
     * @param messageStream the input {@link TrackingEventStream}
     * @return the output {@link Stream} after conversion
     */
    public static <M extends Message<?>> Stream<M> asStream(MessageStream<M> messageStream) {
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
        return stream(spliterator, false);
    }

    private StreamUtils() {
    }


}
