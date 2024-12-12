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

/**
 * Utility methods to work with {@link MessageStream}s.
 */
public abstract class MessageStreamUtils {

    private MessageStreamUtils() {
    }

    /**
     * Creates a Flux containing the elements provided by the given {@code source} MessageStream. Note that multiple
     * invocations of this method on the same {@code source}, or otherwise any components consuming messages from the
     * given {@code source} will cause elements to be consumed by only one of the fluxes or competing consumers.
     *
     * @param source The MessageStream providing the elements
     * @param <M>    The type of Message returned by the source
     * @return a Flux with the elements provided by the source
     */
    public static <M extends Message<?>> Flux<MessageStream.Entry<M>> asFlux(MessageStream<M> source) {
        return Flux.create(emitter -> {
            FluxTask<M> fluxTask = new FluxTask<>(source, emitter);
            emitter.onRequest(i -> fluxTask.process());
            source.onAvailable(fluxTask::process);
        });
    }

    /**
     * Returns a CompletableFuture that completes with the given reduction of messages read from the {@code source}. The
     * reduction is computed by applying the given {@code accumulator} function on the result of the previous invocation
     * in combination with each element returned by the given {@code source}. The very first invocation of the
     * accumulator function is given the {@code identity}.
     * <p>
     * If the given {@code source} completes normally without producing any messages, the returned CompletableFuture
     * completes with the given {@code identity}.
     * <p>
     * If the given {@code source} completes with an error, whether elements have been produced or not, the returned
     * CompletableFuture completes exceptionally with that error.
     * <p>
     * <em>Multi-threading</em><br/>
     * The accumulator function is invoked either on the thread calling this method, when messages are immediately
     * available for reading, or on the thread on which elements are reported to be available for reading from the given
     * {@code source}. The accumulator function does not need to be thread-safe.
     *
     * @param source      The MessageStream to consumer messages from
     * @param identity    The initial value to use for the accumulation
     * @param accumulator The function to combine the current reduction result with the next element from the
     *                    MessageStream
     * @param <M>         The type of Message to consume from the MessageStream
     * @param <R>         The type of result expected from the reduction operation
     * @return a CompletableFuture that completes with the result of the reduction operation
     */
    public static <M extends Message<?>, R> CompletableFuture<R> reduce(MessageStream<M> source,
                                                                        R identity,
                                                                        BiFunction<R, MessageStream.Entry<M>, R> accumulator) {
        Reducer<M, R> reducer = new Reducer<>(source, identity, accumulator);
        source.onAvailable(reducer::process);
        return reducer.completableFuture();
    }

    /**
     * Returns a CompletableFuture that completes with the first element from the given {@code source}.
     * <p>
     * If the given source completed without producing any elements, the returned CompletableFuture will either complete
     * with a {@code null} result, of the source completed normally, or exceptionally if the source completed with an
     * error.
     * <p>
     * Once the first element is read from the source, it is automatically closed and any subsequent elements in the
     * {@code source} are ignored.
     *
     * @param source The source to read the first message from
     * @param <M>    The type of Message produced by the stream
     * @return a CompletableFuture that completes with the first message from the stream
     */
    public static <M extends Message<?>> CompletableFuture<MessageStream.Entry<M>> firstAsCompletableFuture(
            MessageStream<M> source) {
        FirstResult<M> firstResult = new FirstResult<>(source);
        source.onAvailable(firstResult::process);
        return firstResult.completableFuture();
    }

    private static class FluxTask<M extends Message<?>> {

        private final AtomicBoolean processingGate = new AtomicBoolean(false);
        private final MessageStream<M> source;
        private final FluxSink<MessageStream.Entry<M>> emitter;

        public FluxTask(MessageStream<M> source, FluxSink<MessageStream.Entry<M>> emitter) {
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


        private final CompletableFuture<R> completableFuture;
        private final MessageStream<M> source;
        private final BiFunction<R, MessageStream.Entry<M>, R> accumulator;
        private final AtomicBoolean processingGate = new AtomicBoolean(false);

        private final AtomicReference<R> intermediateResult;

        public Reducer(MessageStream<M> source, R identity,
                       BiFunction<R, MessageStream.Entry<M>, R> accumulator) {
            this.source = source;
            this.intermediateResult = new AtomicReference<>(identity);
            this.accumulator = accumulator;
            this.completableFuture = new CompletableFuture<R>();
        }

        public CompletableFuture<R> completableFuture() {
            return completableFuture;
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
                        source.error().ifPresentOrElse(completableFuture::completeExceptionally,
                                                       () -> completableFuture.complete(intermediateResult.get()));
                    }
                } finally {
                    processingGate.set(false);
                }
                continueOnCurrentThread =
                        !completableFuture.isDone() && (source.hasNextAvailable() || source.isCompleted());
            }
        }
    }

    private static class FirstResult<M extends Message<?>> {

        private final MessageStream<M> source;
        private final AtomicBoolean processingGate = new AtomicBoolean(false);
        private final CompletableFuture<MessageStream.Entry<M>> completableFuture = new CompletableFuture<>();

        public FirstResult(MessageStream<M> source) {
            this.source = source;
        }

        public void process() {
            if (!processingGate.getAndSet(true)) {
                try {
                    if (!completableFuture.isDone() && source.hasNextAvailable()) {
                        source.next().ifPresent(completableFuture::complete);
                    }
                    if (source.isCompleted() && !completableFuture.isDone()) {
                        source.error().ifPresentOrElse(completableFuture::completeExceptionally,
                                                       () -> completableFuture.complete(null));
                    }
                } finally {
                    processingGate.set(false);
                }
            }
        }

        public CompletableFuture<MessageStream.Entry<M>> completableFuture() {
            return completableFuture;
        }
    }
}
