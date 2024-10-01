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

import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Represents a stream of entries of type {@code E} that can be consumed as they become available.
 * <p>
 * In most cases, the entry is an implementation of {@link Message}, making this {@link MessageStream stream} useful as
 * a result from method invocations on the buses, the stores, and message handlers.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface MessageStream<E> {

    /**
     * Create a {@link MessageStream stream} that provides the items returned by the given {@code iterable}.
     * <p>
     * Note that each separate consumer of the stream will receive each entry of the given {@code iterable} if the
     * iterable does so.
     *
     * @param iterable The {@link Iterable} providing the {@link Message Messages} to stream.
     * @param <E>      The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} of entries of type {@code E} that returns the entires provided by the
     * given {@code iterable}.
     */
    static <E> MessageStream<E> fromIterable(@NotNull Iterable<E> iterable) {
        return new IterableMessageStream<>(iterable);
    }

    /**
     * Create a {@link MessageStream stream} that provides the entries returned by the given {@code stream}.
     * <p>
     * Note that each separate consumer of the stream will receive each entry of the given {@code stream}, if the stream
     * does so.
     *
     * @param stream The {@link Stream} providing the {@link Message Messages} to stream.
     * @param <E>    The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} of entries of type {@code E} that returns the entries provided by the
     * given {@code stream}.
     */
    static <E> MessageStream<E> fromStream(@NotNull Stream<E> stream) {
        return new StreamMessageStream<>(stream);
    }

    /**
     * Create a {@link MessageStream stream} that provides the entry returned by the given {@code flux}.
     *
     * @param flux The {@link Flux} providing the entries of type {@code E} to stream.
     * @param <E>  The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} of entries of type {@code E} that returns the entries provided by the
     * given {@code flux}.
     */
    static <E> MessageStream<E> fromFlux(@NotNull Flux<E> flux) {
        return new FluxMessageStream<>(flux);
    }

    /**
     * Create a {@link MessageStream stream} that returns the entry when the given {@code future} completes.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the future returns
     * {@code null}. The stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future The {@link CompletableFuture} providing the entry of type {@code E} to contain in the stream.
     * @param <E>    The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} containing at most one entry of type {@code E}.
     */
    static <E> MessageStream<E> fromFuture(@NotNull CompletableFuture<E> future) {
        return new SingleValueMessageStream<>(future);
    }

    /**
     * Create a {@link MessageStream stream} consisting of given {@code instance} as the only entry.
     * <p>
     * Once the entry of type {@code E} is consumed, the stream is considered completed.
     *
     * @param instance The entry of type {@code E} to return in the stream.
     * @param <E>      The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} consisting of a single entry of type {@code E}.
     */
    static <E> MessageStream<E> just(E instance) {
        return new SingleValueMessageStream<>(instance);
    }

    /**
     * Create a {@link MessageStream stream} that completed with given {@code failure}.
     * <p>
     * All attempts to read from this stream will propagate this error.
     *
     * @param failure The {@link Throwable} to propagate to consumers of the stream.
     * @param <E>     The type of entry carried in this {@link MessageStream stream}.
     * @return A {@link MessageStream stream} that is completed exceptionally.
     */
    static <E> MessageStream<E> failed(@NotNull Throwable failure) {
        return new FailedMessageStream<>(failure);
    }

    /**
     * Create a {@link MessageStream stream} that carries no entries of type {@code E} and is considered to be
     * successfully completed.
     * <p>
     * Any attempt to convert this stream to a component that requires an entry to be returned (such as
     * {@link CompletableFuture}), will have it return {@code null}.
     *
     * @param <E> The type of entry carried in this {@link MessageStream stream}.
     * @return An empty {@link MessageStream stream}.
     */
    static <E> MessageStream<E> empty() {
        return EmptyMessageStream.instance();
    }

    /**
     * Returns a {@link CompletableFuture} that completes with the first entry contained in this {@link MessageStream},
     * or exceptionally if the stream completes with an error before returning any entries of type {@code E}.
     * <p>
     * If the stream completes successfully before returning any entries, the {@code CompletableFuture} completes with a
     * {@code null} value.
     *
     * @return A {@link CompletableFuture} that completes with the first entry, {@code null} if it is empty, or
     * exceptionally if the {@link MessageStream stream} propagates an error.
     */
    CompletableFuture<E> asCompletableFuture();

    /**
     * Creates a {@link Flux} that consumes the entries of type {@code E} from this {@link MessageStream stream}.
     * <p>
     * The returned {@code Flux} will complete successfully if the stream does so, and exceptionally if the stream
     * completed with an error.
     *
     * @return A {@link Flux} carrying the entries of type {@code E} of this {@link MessageStream stream}.
     */
    Flux<E> asFlux();

    /**
     * Returns a {@link MessageStream stream} that maps each entry of type {@code E} from this stream using given
     * {@code mapper} function into an entry of type {@code R}.
     * <p>
     * The returned stream completes the same way {@code this} stream completes.
     *
     * @param mapper The function converting entries from this {@link MessageStream stream} from type {@code E} to
     *               {@code R}.
     * @param <R>    The declared type of entry returned in the mapped {@link MessageStream}.
     * @return A {@link MessageStream stream} with all entries of type {@code E} mapped according to the {@code mapper}
     * function.
     */
    default <R> MessageStream<R> map(@NotNull Function<E, R> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    /**
     * Returns a {@link CompletableFuture} of type {@code R}, using the given {@code identity} as the initial value for
     * the given {@code accumulator}.
     * <p>
     * The {@code accumulator} will process all entries of type {@code E} within this {@link MessageStream stream} until
     * a single value of type {@code R} is left.
     * <p>
     * Note that parallel processing <b>is not</b> supported!
     *
     * @param identity    The initial value given to the {@code accumulator}.
     * @param accumulator The {@link BiFunction} accumulating all entries of type {@code E} from this
     *                    {@link MessageStream stream} into a return value of type {@code R}.
     * @param <R>         The result of the {@code accumulator} after reducing all entries of type {@code E} from this
     *                    {@link MessageStream stream}.
     * @return A {@link CompletableFuture} carrying the result of the given {@code accumulator} that reduced the entire
     * {@link MessageStream stream}.
     */
    <R> CompletableFuture<R> reduce(@NotNull R identity,
                                    @NotNull BiFunction<R, E, R> accumulator);

    /**
     * Invokes the given {@code onNext} each time an entry of type {@code E} is consumed from this
     * {@link MessageStream stream}.
     * <p>
     * Depending on the stream's implementation, the function may be invoked when the entry is provided to the
     * {@link Consumer}, or at the moment it's available for reading on the stream. Subscribing multiple times to the
     * resulting stream may cause the given {@code onNext} to be invoked more than once for an entry.
     *
     * @param onNext The {@link Consumer} to invoke for each entry.
     * @return A {@link MessageStream stream} that will invoke the given {@code onNext} for each entry.
     */
    default MessageStream<E> onNextItem(@NotNull Consumer<E> onNext) {
        return new OnNextMessageStream<>(this, onNext);
    }

    /**
     * TODO this is by no means done yet...please fix.
     *
     * @param consumer
     * @return
     */
    default MessageStream<E> consume(@NotNull Predicate<E> consumer) {
        asFlux().takeWhile(consumer).subscribe().dispose();
        return this;
    }

    /**
     * Returns a {@link MessageStream stream} that, when {@code this} stream completes with an error, continues reading
     * from the stream provided by given {@code onError} function.
     *
     * @param onError The {@link Function} providing the replacement {@link MessageStream stream} to continue with after
     *                an exception on {@code this} stream.
     * @return A {@link MessageStream stream} that continues onto another stream when {@code this} stream completes with
     * an error.
     */
    default MessageStream<E> onErrorContinue(@NotNull Function<Throwable, MessageStream<E>> onError) {
        return new OnErrorContinueMessageStream<>(this, onError);
    }

    /**
     * Returns a {@link MessageStream stream} that concatenates this stream with the given {@code other} stream, if this
     * stream completes successfully.
     * <p>
     * When {@code this} stream completes with an error, so does the returned stream.
     *
     * @param other The {@link MessageStream} to append to this stream.
     * @return A {@link MessageStream stream} concatenating this stream with given {@code other}.
     */
    default MessageStream<E> concatWith(@NotNull MessageStream<E> other) {
        return new ConcatenatingMessageStream<>(this, other);
    }

    /**
     * Returns a {@link MessageStream stream} that invokes the given {@code completeHandler} when the stream completes
     * normally.
     *
     * @param completeHandler The {@link Runnable} to invoke when the {@link MessageStream stream} completes normally.
     * @return A {@link MessageStream stream} that invokes the {@code completeHandler} upon normal completion.
     */
    default MessageStream<E> whenComplete(@NotNull Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }
}
