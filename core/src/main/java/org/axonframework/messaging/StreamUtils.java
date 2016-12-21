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
