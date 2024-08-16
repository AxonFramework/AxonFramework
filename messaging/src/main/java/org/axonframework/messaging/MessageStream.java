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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents a stream of {@link Message Messages} that can be consumed as they become available.
 *
 * @param <M> The type of {@link Message} carried in this stream
 * @author Allard Buijze
 * @since 5.0.0
 */
public interface MessageStream<M extends Message<?>> {

    /**
     * Create a {@link MessageStream stream} that provides the items returned by the given {@code iterable}. Note that
     * each separate consumer of the stream will receive each item of the given {@code iterable} if the iterable does
     * so.
     *
     * @param iterable The iterable providing the messages to stream.
     * @param <M>      The declared type of Message in this stream.
     * @return A {@link MessageStream stream} of {@link Message Messages} that returns the messages provided by the
     * given {@code iterable}.
     */
    static <M extends Message<?>> MessageStream<M> fromIterable(Iterable<M> iterable) {
        return new IterableMessageStream<>(iterable);
    }

    /**
     * Create a {@link MessageStream stream} that returns the message when the given {@code future} completes. The
     * stream will contain at most a single item. It may also contain no items if the future returns {@code null}.
     * <p>
     * The stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future The future providing the {@link Message} to contain in the stream.
     * @param <M>    The declared type of {@link Message} in this stream.
     * @return A {@link MessageStream stream} containing at most one {@link Message}.
     */
    static <M extends Message<?>> MessageStream<M> fromFuture(CompletableFuture<M> future) {
        return new SingleValueMessageStream<>(future);
    }

    /**
     * Create a {@link MessageStream stream} that provides the items returned by the given {@code stream}. Note that
     * each separate consumer of the stream will receive each item of the given {@code stream}, if the stream does so.
     *
     * @param stream The {@link Stream} providing the items to contain in the {@link MessageStream}.
     * @param <M>    The declared type of {@link Message} in this stream.
     * @return A {@link MessageStream stream} returning each of the messages returned by the stream.
     */
    static <M extends Message<?>> MessageStream<M> fromStream(Stream<M> stream) {
        return new StreamMessageStream<>(stream);
    }

    /**
     * Create a {@link MessageStream stream} consisting of given {@code instance} as the only {@link Message}. Once the
     * {@code Message} is consumer, the stream is considered completed.
     *
     * @param instance The {@link Message} to return in the stream.
     * @param <M>      The declared type of {@link Message} in this stream.
     * @return A {@link MessageStream stream} consisting of a single {@link Message}.
     */
    static <M extends Message<?>> MessageStream<M> just(M instance) {
        return new SingleValueMessageStream<>(instance);
    }

    /**
     * Create a {@link MessageStream stream} that completed with given {@code failure}. All attempts to read from this
     * stream will propagate this error.
     *
     * @param failure The error to propagate to consumers of the stream.
     * @param <M>     The declared type of {@link Message} in this stream.
     * @return A {@link MessageStream stream} that is completed exceptionally.
     */
    static <M extends Message<?>> MessageStream<M> failed(Throwable failure) {
        return new FailedMessageStream<>(failure);
    }

    /**
     * Create a {@link MessageStream stream} that carries no {@link Message Messages} and is considered to be
     * successfully completed. Any attempt to convert this stream to a component that requires an item to be returned
     * (such as {@link CompletableFuture}), will have it return {@code null}.
     *
     * @param <M> The declared type of {@link Message} in this stream.
     * @return An empty {@link MessageStream stream}.
     */
    static <M extends Message<?>> MessageStream<M> empty() {
        return EmptyMessageStream.instance();
    }

    /**
     * Returns a {@link CompletableFuture} that completes with the first item contained in this {@link MessageStream},
     * or exceptionally if the stream completes with an error before returning any {@link Message Messages}. If the
     * stream completes successfully before returning any items, the {@code CompletableFuture} completes with a
     * {@code null} value.
     *
     * @return A {@link CompletableFuture} that completes with the first item, {@code null} if it is empty, or
     * exceptionally if the {@link MessageStream stream} propagates an error.
     */
    CompletableFuture<M> asCompletableFuture();

    /**
     * Creates a {@link Flux} that consumers the items from this {@link MessageStream stream}. The returned {@code Flux}
     * will complete successfully if the stream does so, and exceptionally if the stream completed with an error.
     *
     * @return A {@link Flux} carrying the items of this {@link MessageStream stream}.
     */
    Flux<M> asFlux();

    /**
     * Returns a {@link MessageStream stream} that concatenates this stream with the given {@code other} stream, if this
     * stream completes successfully. If this stream completes with an error, so does the returned stream.
     *
     * @param other The {@link MessageStream} to append to this stream.
     * @return A {@link MessageStream stream} concatenating this stream with given {@code other}.
     */
    default MessageStream<M> concatWith(MessageStream<M> other) {
        return new ConcatenatingMessageStream<>(this, other);
    }

    /**
     * Returns a {@link MessageStream stream} that maps each item from this stream using given {@code mapper} function.
     * The returned stream completes the same way this stream completes.
     *
     * @param mapper The function converting items from this {@link MessageStream stream}.
     * @param <R>    The declared type of {@link Message} returned in the mapped {@link MessageStream}.
     * @return A {@link MessageStream stream} with all items mapped according to the {@code mapper} function.
     */
    default <R extends Message<?>> MessageStream<R> map(Function<M, R> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    /**
     * Returns a {@link MessageStream stream} that invokes the given {@code completeHandler} when the stream completes
     * normally.
     *
     * @param completeHandler The handler to invoke when the {@link MessageStream stream} completes normally.
     * @return A {@link MessageStream stream} that invokes the {@code completeHandler} upon normal completion.
     */
    default MessageStream<M> whenComplete(Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }

    /**
     * Returns a {@link MessageStream stream} that, when {@code this} stream completes with an error, continues reading
     * from the stream provided by given {@code onError} function.
     *
     * @param onError The function providing the stream to continue with.
     * @return A {@link MessageStream stream} that continues onto another stream when this stream completes with an
     * error.
     */
    default MessageStream<M> onErrorContinue(Function<Throwable, MessageStream<M>> onError) {
        return new OnErrorContinueMessageStream<>(this, onError);
    }

    /**
     * Invokes the given {@code handler} each time an item is consumed from this {@link MessageStream stream}. Depending
     * on the stream's implementation, the function may be invoked when the item is provided to the consumer, or at the
     * moment it's available for reading on the stream. Subscribing multiple times to the resulting stream may cause the
     * given {@code handler} to be invoked more than once for an item.
     *
     * @param handler The {@link Consumer} to invoke for each item.
     * @return A {@link MessageStream stream} that will invoke the given {@code handler} for each item.
     */
    default MessageStream<M> onNextItem(Consumer<M> handler) {
        return new OnItemMessageStream<>(this, handler);
    }
}
