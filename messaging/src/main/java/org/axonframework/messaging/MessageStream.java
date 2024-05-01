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
 * Represents a stream of Messages that can be consumed as they become available
 *
 * @param <T> The type of Message carried in this stream
 */
public interface MessageStream<T extends Message<?>> {

    /**
     * Create a stream that provides the items returned by the given {@code iterable}. Note that each separate consumer
     * of the stream will receive each item of the given {@code iterable} if the iterable does so.
     *
     * @param iterable The iterable providing the messages to stream
     * @param <T>      The declared type of Message in this stream
     * @return a Stream of messages that returns the messages provided by the given {@code iterable}
     */
    static <T extends Message<?>> MessageStream<T> fromIterable(Iterable<T> iterable) {
        return new IterableMessageStream<>(iterable);
    }

    /**
     * Create a MessageStream that returns the message when the given {@code future} completes. The Stream will contain
     * at most a single item (it may also contain no items if the future returns {@code null}.
     * <p>
     * The Stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future The future providing the Message to contain in the stream
     * @param <T>    The declared type of Message in this stream
     * @return a stream containing at most one message
     */
    static <T extends Message<?>> MessageStream<T> fromFuture(CompletableFuture<T> future) {
        return new SingleValueMessageStream<>(future);
    }

    /**
     * Create a stream that provides the items returned by the given {@code stream}. Note that each separate consumer of
     * the stream will receive each item of the given {@code stream}, if the stream does so.
     *
     * @param stream The Stream providing the items to contain in the MessageStream
     * @param <T>    The declared type of Message in this stream
     * @return a MessageStream returning each of the messages returned by the stream
     */
    static <T extends Message<?>> MessageStream<T> fromStream(Stream<T> stream) {
        return new StreamMessageStream<>(stream);
    }

    /**
     * Create a MessageStream consisting of given {@code instance} as the only Message. Once the Message is consumer,
     * the stream is considered completed.
     *
     * @param instance The Message to return in the Stream
     * @param <T>      The declared type of Message in this stream
     * @return a Stream consisting of a single Message
     */
    static <T extends Message<?>> MessageStream<T> just(T instance) {
        return new SingleValueMessageStream<>(instance);
    }

    /**
     * Create a Stream that completed with given {@code failure}. All attempts to read from this stream will propagate
     * this error.
     *
     * @param failure The error to propagate to consumers of the stream
     * @param <T>     The declared type of Message in this stream
     * @return a MessageStream that is completed exceptionally
     */
    static <T extends Message<?>> MessageStream<T> failed(Throwable failure) {
        return new FailedMessageStream<>(failure);
    }

    /**
     * Create a Stream that carries no Messages and is considered to be successfully completed. Any attempt to convert
     * this stream to a component that requires an item to be returned (such as CompletableFuture), will have it return
     * {@code null}.
     *
     * @param <T> The declared type of Message in this stream
     * @return en empty MessageStream
     */
    static <T extends Message<?>> MessageStream<T> empty() {
        return EmptyMessageStream.instance();
    }


    /**
     * Returns a CompletableFuture that completes with the first item contained in this stream, or exceptionally if the
     * Stream completes with an error before returning any Messages. If the stream completes successfully before
     * returning any items, the CompletableFuture completes with a {@code null} value.
     *
     * @return a CompletableFuture that completes with the first item, {@code null} if it is empty, or exceptionally if
     * the Stream propagates an error.
     */
    CompletableFuture<T> asCompletableFuture();

    /**
     * Creates a Flux that consumers the items from this Stream. The returned Flux will complete successfully if the
     * stream does so, and exceptionally if the stream completed with an error.
     *
     * @return a Flux carrying the items of this Stream
     */
    Flux<T> asFlux();

    /**
     * Returns a Stream that concatenates this stream with the given {@code other} stream, if this stream completes
     * successfully. If this stream completes with an error, so does the returned Stream.
     *
     * @param other The stream to append to this stream
     * @return a Stream concatenating this stream with given {@code other}
     */
    default MessageStream<T> concatWith(MessageStream<T> other) {
        return new ConcatenatingMessageStream<>(this, other);
    }

    /**
     * Returns a Stream that maps each item from this stream using given {@code mapper} function. The returned stream
     * completes the same way this stream completes.
     *
     * @param mapper The function converting items from this stream
     * @param <R>    The declared type of Message returned in the mapped MessageStream
     * @return a Stream with all items mapped according to the mapper function
     */
    default <R extends Message<?>> MessageStream<R> map(Function<T, R> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    /**
     * Returns a Stream that invokes the given {@code completeHandler} when the stream completes normally.
     *
     * @param completeHandler The handler to invoke when the stream completes
     * @return A stream that invokes the {@code completeHandler} upon normal completion.
     */
    default MessageStream<T> whenComplete(Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }

    /**
     * Returns a Stream that, when this stream completes with an error, continues reading from the Stream provided by
     * given {@code onError} function.
     *
     * @param onError THe function providing the stream to continue with
     * @return a MessageStream that continues onto another stream when this stream completes with an error
     */
    default MessageStream<T> onErrorContinue(Function<Throwable, MessageStream<T>> onError) {
        return new OnErrorContinueMessageStream<>(this, onError);
    }

    /**
     * Invokes the given {@code handler} each time an item is consumed from this stream. Depending on the stream's
     * implementation, the function may be invoked when the item is provided to the consumer, or at the moment it's
     * available for reading on the stream. Subscribing multiple times to the resulting stream may cause the given
     * {@code function} to be invoked more than once for an item.
     *
     * @param handler The handler to invoke for each item
     * @return a stream that will invoke a handler for each item
     */
    default MessageStream<T> onNextItem(Consumer<T> handler) {
        return new OnItemMessageStream<>(this, handler);
    }
}
