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

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;

/**
 * Utility methods to work with {@link MessageStream MessageStreams}.
 *
 * @since 5.0.0
 * @author Allard Buijze
 */
public abstract class MessageStreamUtils {

    private MessageStreamUtils() {
    }

    /**
     * Creates a Flux containing the {@link MessageStream.Entry entries} provided by the given {@code source}. Note
     * that multiple invocations of this method on the same {@code source}, or otherwise any components consuming
     * entries from the given {@code source} will cause entries to be consumed by only one of the fluxes or competing
     * consumers.
     *
     * @param source The MessageStream providing the elements
     * @param <M>    The type of Message returned by the source
     * @return a Flux with the elements provided by the source
     */
    public static <M extends Message<?>> Flux<MessageStream.Entry<M>> asFlux(@Nonnull MessageStream<M> source) {
        return Flux.create(emitter -> {
            FluxStreamAdapter<M> fluxTask = new FluxStreamAdapter<>(source, emitter);
            emitter.onRequest(i -> fluxTask.process());
            source.onAvailable(fluxTask::process);
        });
    }

    /**
     * Returns a {@code CompletableFuture} that completes with the given reduction of messages read from the {@code source}. The
     * reduction is computed by applying the given {@code accumulator} function on the result of the previous invocation
     * in combination with each {@link MessageStream.Entry entry} returned by the given {@code source}. The very first 
     * invocation of the accumulator function is given the {@code identity}.
     * <p>
     * If the given {@code source} completes normally without producing any entries, the returned {@code CompletableFuture}
     * completes with the given {@code identity}.
     * <p>
     * If the given {@code source} completes with an error, whether entries have been produced or not, the returned
     * {@code CompletableFuture} completes exceptionally with that error.
     * <p>
     * <em>Multi-threading</em><br/>
     * The accumulator function is invoked either on the thread calling this method, when entries are immediately
     * available for reading, or on the thread on which entries are reported to be available for reading from the given
     * {@code source}. The accumulator function does not need to be thread-safe.
     *
     * @param source      The {@link MessageStream} to consume messages from.
     * @param identity    The initial value to use for the accumulation.
     * @param accumulator The function to combine the current reduction result with the next
     *                    {@link MessageStream.Entry entry} from the {@link MessageStream}.
     * @param <M>         The type of {@link Message} to consume from the {@link MessageStream}.
     * @param <R>         The type of result expected from the reduction operation.
     * @return A {@code CompletableFuture} that completes with the result of the reduction operation.
     */
    public static <M extends Message<?>, R> CompletableFuture<R> reduce(@Nonnull MessageStream<M> source,
                                                                        @Nonnull R identity,
                                                                        @Nonnull BiFunction<R, MessageStream.Entry<M>, R> accumulator) {
        Reducer<M, R> reducer = new Reducer<>(source, identity, accumulator);
        source.onAvailable(reducer::process);
        return reducer.result();
    }

    /**
     * Returns a {@code CompletableFuture} that completes with the first {@link MessageStream.Entry entry} from the
     * given {@code source}.
     * <p>
     * If the given source has completed without producing any entries, the returned {@code CompletableFuture} will
     * either complete with a {@code null} result if the source completed normally, or exceptionally if the source
     * completed with an error.
     * <p>
     * Once the first entry is read from the source, it is automatically closed, and any subsequent entries in the
     * {@code source} are ignored.
     *
     * @param source The source to read the first {@link MessageStream.Entry entry} from.
     * @param <M>    The type of {@link Message} produced by the stream.
     * @return A {@code CompletableFuture} that completes with the first {@link MessageStream.Entry entry} from the
     *         stream.
     */
    public static <M extends Message<?>> CompletableFuture<MessageStream.Entry<M>> firstAsCompletableFuture(
            MessageStream<M> source) {
        FirstResult<M> firstResult = new FirstResult<>(source);
        source.onAvailable(firstResult::process);
        return firstResult.result();
    }

    private static class FluxStreamAdapter<M extends Message<?>> {

        private final AtomicBoolean processingGate = new AtomicBoolean(false);
        private final MessageStream<M> source;
        private final FluxSink<MessageStream.Entry<M>> emitter;

        public FluxStreamAdapter(MessageStream<M> source, FluxSink<MessageStream.Entry<M>> emitter) {
            this.source = source;
            this.emitter = emitter;
        }

        public void process() {
            boolean continueOnCurrentThread = true;
            while (continueOnCurrentThread && !processingGate.getAndSet(true)) {
                try {
                    while (emitter.requestedFromDownstream() > 0 && source.hasNextAvailable()) {
                        source.next().ifPresent(emitter::next);
                    }
                    if (source.isCompleted()) {
                        source.error().ifPresentOrElse(emitter::error, emitter::complete);
                    }
                } catch (Exception e) {
                    emitter.error(e);
                    source.close();
                } finally {
                    processingGate.set(false);
                }
                continueOnCurrentThread = emitter.requestedFromDownstream() > 0 && source.hasNextAvailable();
            }
        }
    }

    private static class Reducer<M extends Message<?>, R> {


        private final CompletableFuture<R> result;
        private final MessageStream<M> source;
        private final BiFunction<R, MessageStream.Entry<M>, R> accumulator;
        private final AtomicBoolean processingGate = new AtomicBoolean(false);

        private final AtomicReference<R> intermediateResult;

        public Reducer(MessageStream<M> source, R identity,
                       BiFunction<R, MessageStream.Entry<M>, R> accumulator) {
            this.source = source;
            this.intermediateResult = new AtomicReference<>(identity);
            this.accumulator = accumulator;
            this.result = new CompletableFuture<>();
        }

        public CompletableFuture<R> result() {
            return result;
        }

        public void process() {
            boolean continueOnCurrentThread = true;
            while (continueOnCurrentThread && !processingGate.getAndSet(true)) {
                try {
                    while (source.hasNextAvailable()) {
                        Optional<MessageStream.Entry<M>> nextItem = source.next();
                        nextItem.ifPresent(e -> intermediateResult.updateAndGet(i -> accumulator.apply(i, e)));
                    }
                    if (source.isCompleted()) {
                        source.error().ifPresentOrElse(result::completeExceptionally,
                                                       () -> result.complete(intermediateResult.get()));
                    }
                } finally {
                    processingGate.set(false);
                }
                continueOnCurrentThread =
                        !result.isDone() && (source.hasNextAvailable() || source.isCompleted());
            }
        }
    }

    private static class FirstResult<M extends Message<?>> {

        private final MessageStream<M> source;
        private final AtomicBoolean processingGate = new AtomicBoolean(false);
        private final CompletableFuture<MessageStream.Entry<M>> result = new CompletableFuture<>();

        public FirstResult(MessageStream<M> source) {
            this.source = source;
        }

        public void process() {
            if (!processingGate.getAndSet(true)) {
                try {
                    if (!result.isDone() && source.hasNextAvailable()) {
                        source.next().ifPresent(result::complete);
                    }
                    if (source.isCompleted() && !result.isDone()) {
                        source.error().ifPresentOrElse(result::completeExceptionally,
                                                       () -> result.complete(null));
                    }
                } finally {
                    processingGate.set(false);
                }
            }
        }

        public CompletableFuture<MessageStream.Entry<M>> result() {
            return result;
        }
    }
}
