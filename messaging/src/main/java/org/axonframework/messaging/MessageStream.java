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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.SimpleContext;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a stream of {@link Entry entries} containing {@link Message Messages} of type {@code M} that can be
 * consumed as they become available.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Ivan Dugalic
 * @author Milan Savić
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface MessageStream<M extends Message<?>> {

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code iterable}, automatically wrapped in an {@link Entry}.
     * <p>
     * Note that each separate consumer of the stream will receive each message if the iterable does so.
     *
     * @param iterable The {@link Iterable} providing the {@link Message Messages} to stream.
     * @param <M>      The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that return the {@link Message Messages} provided
     * by the given {@code iterable}.
     */
    static <M extends Message<?>> MessageStream<M> fromIterable(@Nonnull Iterable<M> iterable) {
        return fromIterable(iterable, message -> new SimpleContext());
    }

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code iterable}, automatically wrapped in an {@link Entry} with the resulting {@link Context} from the
     * {@code contextSupplier}.
     * <p>
     * Note that each separate consumer of the stream will receive each message if the iterable does so.
     *
     * @param iterable        The {@link Iterable} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code iterable}
     *                        returning the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped
     *                        in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that return the {@link Message Messages} provided
     * by the given {@code iterable} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromIterable(@Nonnull Iterable<M> iterable,
                                                                @Nonnull Function<M, Context> contextSupplier) {
        return new IterableMessageStream<>(
                StreamSupport.stream(iterable.spliterator(), false)
                             .<Entry<M>>map(message -> new SimpleEntry<>(message, contextSupplier.apply(message)))
                             .toList()
        );
    }

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code stream}, automatically wrapped in an {@link Entry}.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream The {@link Stream} providing the {@link Message Messages} to stream.
     * @param <M>    The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that return the {@link Message Messages} provided
     * by the given {@code stream}.
     */
    static <M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<M> stream) {
        return fromStream(stream, message -> new SimpleContext());
    }

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code stream}, automatically wrapped in an {@link Entry} with the resulting {@link Context} from the
     * {@code contextSupplier}.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream          The {@link Stream} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code stream} returning
     *                        the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that return the {@link Message Messages} provided
     * by the given {@code stream} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<M> stream,
                                                              @Nonnull Function<M, Context> contextSupplier) {
        return new StreamMessageStream<>(
                stream.map(message -> new SimpleEntry<>(message, contextSupplier.apply(message)))
        );
    }

    /**
     * Create a {@link MessageStream stream} that provides the items of type {@code T} returned by the given
     * {@code stream}, automatically wrapped in an {@link Entry} with the resulting {@link Message} and {@link Context}
     * from the {@code messageSupplier} and the {@code contextSupplier} respectively.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream          The {@link Stream} providing the items of type {@code T} to map to a {@link Message} and
     *                        {@link Context}.
     * @param messageSupplier A {@link Function} ingesting each item of type {@code T} from the given {@code stream}
     *                        returning the {@link Message} to set for the {@link Entry} to add in the resulting
     *                        {@link MessageStream}.
     * @param contextSupplier A {@link Function} ingesting each item of type {@code T} from the given {@code stream}
     *                        returning the {@link Context} to set for the {@link Entry} to add in the resulting
     *                        {@link MessageStream}.
     * @param <T>             The type of item contained in the given {@code stream} that will be mapped to a
     *                        {@link Message} and {@link Context} by the {@code messageSupplier} and
     *                        {@code contextSupplier} respectively.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that return the {@link Message Messages}
     * resulting from the given {@code messageSupplier} with a {@link Context} provided by the
     * {@code contextSupplier}.
     */
    static <T, M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<T> stream,
                                                                 @Nonnull Function<T, M> messageSupplier,
                                                                 @Nonnull Function<T, Context> contextSupplier) {
        return new StreamMessageStream<>(
                stream.map(item -> new SimpleEntry<>(messageSupplier.apply(item), contextSupplier.apply(item)))
        );
    }

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code flux}, automatically wrapped in an {@link Entry}.
     *
     * @param flux The {@link Flux} providing the {@link Message Messages} to stream.
     * @param <M>  The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that returns the {@link Message Messages}
     * provided by the given {@code flux}.
     */
    static <M extends Message<?>> MessageStream<M> fromFlux(@Nonnull Flux<M> flux) {
        return fromFlux(flux, message -> new SimpleContext());
    }

    /**
     * Create a {@link MessageStream stream} that provides the {@link Message Messages} returned by the given
     * {@code flux}, automatically wrapped in an {@link Entry} with the resulting {@link Context} from the
     * {@code contextSupplier}.
     *
     * @param flux            The {@link Flux} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code flux} returning
     *                        the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} of {@link Entry entries} that returns the {@link Message Messages}
     * provided by the given {@code flux} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromFlux(@Nonnull Flux<M> flux,
                                                            @Nonnull Function<M, Context> contextSupplier) {
        return new FluxMessageStream<>(flux.map(message -> new SimpleEntry<>(message, contextSupplier.apply(message))));
    }

    /**
     * Create a {@link MessageStream stream} that returns an {@link Entry entry} wrapping the {@link Message} from the
     * given {@code future}, once the given {@code future} completes.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the future returns
     * {@code null}. The stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future The {@link CompletableFuture} providing the {@link Message} to contain in the stream.
     * @param <M>    The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} containing at most one {@link Entry entry} from the given {@code future}.
     */
    static <M extends Message<?>> MessageStream<M> fromFuture(@Nonnull CompletableFuture<M> future) {
        return fromFuture(future, message -> new SimpleContext());
    }

    /**
     * Create a {@link MessageStream stream} that returns an {@link Entry entry} wrapping the {@link Message} from the
     * given {@code future}, once the given {@code future} completes.
     * <p>
     * The automatically generated {@code Entry} will have the {@link Context} as given by the {@code contextSupplier}.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the future returns
     * {@code null}. The stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future          The {@link CompletableFuture} providing the {@link Message} to contain in the stream.
     * @param contextSupplier A {@link Function} ingesting the {@link Message} from the given {@code future} returning
     *                        the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} containing at most one {@link Entry entry} from the given {@code future}
     * with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromFuture(@Nonnull CompletableFuture<M> future,
                                                              @Nonnull Function<M, Context> contextSupplier) {
        return new SingleValueMessageStream<>(
                future.thenApply(message -> new SimpleEntry<>(message, contextSupplier.apply(message)))
        );
    }

    /**
     * Create a {@link MessageStream stream} containing the given {@code message} automatically wrapped in an
     * {@link Entry}.
     * <p>
     * Once the {@code Entry} is consumed, the stream is considered completed.
     *
     * @param message The {@link Message} to wrap in an {@link Entry} and return in the stream.
     * @param <M>     The type of {@link Message} given.
     * @return A {@link MessageStream stream} consisting of a single {@link Entry entry} wrapping the given
     * {@code message}.
     */
    static <M extends Message<?>> MessageStream<M> just(@Nullable M message) {
        return just(message, m -> new SimpleContext());
    }

    /**
     * Create a {@link MessageStream stream} containing the given {@code message} automatically wrapped in an
     * {@link Entry}.
     * <p>
     * Once the {@code Entry} is consumed, the stream is considered completed.
     *
     * @param message         The {@link Message} to wrap in an {@link Entry} and return in the stream.
     * @param contextSupplier A {@link Function} ingesting the given {@code message} returning the {@link Context} to
     *                        set for the {@link Entry} the {@code message} is wrapped in.
     * @param <M>             The type of {@link Message} given.
     * @return A {@link MessageStream stream} consisting of a single {@link Entry entry} wrapping the given
     * {@code message} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> just(@Nullable M message,
                                                        @Nonnull Function<M, Context> contextSupplier) {
        return new SingleValueMessageStream<>(new SimpleEntry<>(message, contextSupplier.apply(message)));
    }

    /**
     * Create a {@link MessageStream stream} that completed with given {@code failure}.
     * <p>
     * All attempts to read from this stream will propagate this error.
     *
     * @param failure The {@link Throwable} to propagate to consumers of the stream.
     * @param <M>     The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} that is completed exceptionally.
     */
    static <M extends Message<?>> MessageStream<M> failed(@Nonnull Throwable failure) {
        return new FailedMessageStream<>(failure);
    }

    /**
     * Create a {@link MessageStream stream} that carries no {@link Entry entries} and is considered to be successfully
     * completed.
     * <p>
     * Any attempt to convert this stream to a component that requires an entry to be returned (such as
     * {@link CompletableFuture}), will have it return {@code null}.
     *
     * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return An empty {@link MessageStream stream}.
     */
    static <M extends Message<?>> MessageStream<M> empty() {
        return EmptyMessageStream.instance();
    }

    /**
     * Returns a {@link CompletableFuture} that completes with the <b>first</b> {@link Entry entry} contained in this
     * {@link MessageStream}, or exceptionally if the stream completes with an error before returning any entries.
     * <p>
     * If the stream completes successfully before returning any entries, the {@code CompletableFuture} completes with a
     * {@code null} value.
     *
     * @return A {@link CompletableFuture} that completes with the first {@link Entry entry}, {@code null} if it is
     * empty, or exceptionally if the {@link MessageStream stream} propagates an error.
     */
    CompletableFuture<Entry<M>> firstAsCompletableFuture();

    /**
     * Creates a {@link Flux} that consumes the {@link Entry entries} from this {@link MessageStream stream}.
     * <p>
     * The returned {@code Flux} will complete successfully if the stream does so, and exceptionally if the stream
     * completed with an error.
     *
     * @return A {@link Flux} carrying the {@link Entry entries} of this {@link MessageStream stream}.
     */
    Flux<Entry<M>> asFlux();

    /**
     * Returns a {@link MessageStream stream} that maps each {@link Entry entry} from this stream using given
     * {@code mapper} function into an entry carrying a {@code MessageEntry} with a {@link Message} of type {@code RM}.
     * <p>
     * The returned stream completes the same way {@code this} stream completes.
     *
     * @param mapper The function converting {@link Entry entries} from this {@link MessageStream stream} from entries
     *               containing {@link Message message} of type {@code M} to {@code RM}.
     * @param <RM>   The declared type of {@link Message} contained in the returned {@link Entry entry}.
     * @return A {@link MessageStream stream} with all {@link Entry entries} mapped according to the {@code mapper}
     * function.
     */
    default <RM extends Message<?>> MessageStream<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    /**
     * Returns a {@link MessageStream stream} that maps each {@link Entry#message() message} from the
     * {@link Entry entries} in this stream using the given {@code mapper} function. This maps the
     * {@link Message Messages} from type {@code M} to type {@code RM}.
     * <p>
     * The returned stream completes the same way {@code this} stream completes.
     *
     * @param mapper The function converting {@link Entry#message() message} from the {@link Entry entries} in this
     *               {@link MessageStream stream} from type {@code M} to {@code RM}.
     * @param <RM>   The declared type of {@link Message} contained in the returned {@link Entry entry}.
     * @return A {@link MessageStream stream} with all {@link Entry entries} mapped according to the {@code mapper}
     * function.
     */
    default <RM extends Message<?>> MessageStream<RM> mapMessage(@Nonnull Function<M, RM> mapper) {
        return map(entry -> entry.map(mapper));
    }

    /**
     * Returns a {@link CompletableFuture} of type {@code R}, using the given {@code identity} as the initial value for
     * the given {@code accumulator}.
     * <p>
     * The {@code accumulator} will process all {@link Entry entries} within this {@link MessageStream stream} until a
     * single value of type {@code R} is left.
     * <p>
     * Note that parallel processing <b>is not</b> supported!
     *
     * @param identity    The initial value given to the {@code accumulator}.
     * @param accumulator The {@link BiFunction} accumulating all {@link Entry entries} from this
     *                    {@link MessageStream stream} into a return value of type {@code R}.
     * @param <R>         The result of the {@code accumulator} after reducing all {@link Entry entries} from this
     *                    {@link MessageStream stream}.
     * @return A {@link CompletableFuture} carrying the result of the given {@code accumulator} that reduced the entire
     * {@link MessageStream stream}.
     */
    <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                    @Nonnull BiFunction<R, Entry<M>, R> accumulator);

    /**
     * Invokes the given {@code onNext} each time an {@link Entry entry} is consumed from this
     * {@link MessageStream stream}.
     * <p>
     * Depending on the stream's implementation, the function may be invoked when the entry is provided to the
     * {@link Consumer}, or at the moment it's available for reading on the stream. Subscribing multiple times to the
     * resulting stream may cause the given {@code onNext} to be invoked more than once for an entry.
     *
     * @param onNext The {@link Consumer} to invoke for each {@link Entry entry}.
     * @return A {@link MessageStream stream} that will invoke the given {@code onNext} for each {@link Entry entry}.
     */
    default MessageStream<M> onNext(@Nonnull Consumer<Entry<M>> onNext) {
        return new OnNextMessageStream<>(this, onNext);
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
    default MessageStream<M> onErrorContinue(@Nonnull Function<Throwable, MessageStream<M>> onError) {
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
    default MessageStream<M> concatWith(@Nonnull MessageStream<M> other) {
        return new ConcatenatingMessageStream<>(this, other);
    }

    /**
     * Returns a {@link MessageStream stream} that invokes the given {@code completeHandler} when the stream completes
     * normally.
     *
     * @param completeHandler The {@link Runnable} to invoke when the {@link MessageStream stream} completes normally.
     * @return A {@link MessageStream stream} that invokes the {@code completeHandler} upon normal completion.
     */
    default MessageStream<M> whenComplete(@Nonnull Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }

    /**
     * A {@link MessageStream}-specific container of {@link Message} implementations.
     * <p>
     * May be implemented to support {@link Entry entries} that contain several objects. As such, this interface may be
     * regarded as a tuple.
     *
     * @param <M> The type of {@link Message} contained in this {@link Entry} implementation.
     * @author Allard Buijze
     * @author Milan Savić
     * @author Mitchell Herrijgers
     * @author Steven van Beelen
     * @since 5.0.0
     */
    interface Entry<M extends Message<?>> extends Context {

        /**
         * Returns the {@link Message} implementation contained by this {@link Entry}.
         *
         * @return The {@link Message} implementation contained by this {@link Entry}.
         */
        M message();

        /**
         * Maps the {@link #message()} by running it through the given {@code mapper}. This adjusts the contained
         * {@link #message()} into a {@link Message} implementation of type {@code RM}.
         *
         * @param mapper A {@link Function} mapping the {@link #message()} of type {@code M} to a {@link Message} of
         *               type {@code RM}.
         * @param <RM>   The declared type of {@link Message} resulting from the given {@code mapper}.
         * @return The result of running the current {@link #message()} through the given {@code mapper}.
         */
        <RM extends Message<?>> Entry<RM> map(@Nonnull Function<M, RM> mapper);
    }
}
