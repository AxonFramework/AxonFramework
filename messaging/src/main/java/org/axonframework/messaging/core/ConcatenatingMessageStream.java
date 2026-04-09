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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Implementation of the {@link MessageStream} that concatenates two {@code MessageStreams}.
 * <p>
 * Will only start streaming {@link Entry entries} from the {@code second MessageStream} when the
 * {@code first MessageStream} completes successfully.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author John Hendrikx
 * @since 5.0.0
 */
class ConcatenatingMessageStream<M extends Message> extends AbstractMessageStream<M> {

    private final List<MessageStream<M>> streams;

    private MessageStream<M> active;

    /**
     * Construct a {@link MessageStream stream} that initially consume from the {@code first MessageStream}, followed by
     * the {@code second} if the {@code first MessageStream} completes successfully
     *
     * @param first  The initial {@link MessageStream stream} to consume entries from.
     * @param second The second {@link MessageStream stream} to start consuming from once the {@code first} stream
     *               completes successfully.
     */
    ConcatenatingMessageStream(MessageStream<M> first,
                               MessageStream<M> second) {
        this.streams = new ArrayList<>(List.of(first, second));

        switchStream();
    }

    @Override
    protected synchronized FetchResult<Entry<M>> fetchNext() {
        do {
            if (active.hasNextAvailable()) {
                return FetchResult.of(active.next().orElseThrow());
            }

            if (!active.isCompleted()) {
                return FetchResult.notReady();
            }

            if (active.error().isPresent()) {
                return FetchResult.error(active.error().get());
            }
        } while(switchStream());

        return FetchResult.completed();
    }

    private boolean switchStream() {
        if (active != null) {
            active.setCallback(() -> {});
            active.close();
        }

        if (streams.isEmpty()) {
            return false;
        }

        this.active = streams.removeFirst();

        active.setCallback(() -> {
            boolean error = active.error().isPresent();

            // Close immediately on terminal error
            if (error) {
                close();
            }

            // Signal progress if not completed, there was an error, or final stream was finished
            if (!active.isCompleted() || error || streams.isEmpty()) {
                signalProgress();
            }
        });

        return true;
    }

    @Override
    public synchronized void close() {
        active.close();
        streams.forEach(MessageStream::close);
    }

    @Override
    public synchronized <R> CompletableFuture<R> reduce(R identity,
                                           BiFunction<R, Entry<M>, R> accumulator) {
        CompletableFuture<R> reduction = active.reduce(identity, accumulator);

        for (MessageStream<M> stream : streams) {
            reduction = reduction.thenCompose(intermediate -> stream.reduce(intermediate, accumulator));
        }

        return reduction;
    }
}
