/*
 * Copyright (c) 2010-2025. Axon Framework
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
     * Create a stream that provides the {@link Message Messages} returned by the given {@code iterable}, automatically
     * wrapped in an {@link Entry}.
     * <p>
     * Note that each separate consumer of the stream will receive each message if the iterable does so.
     *
     * @param iterable The {@link Iterable} providing the {@link Message Messages} to stream.
     * @param <M>      The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that return the {@link Message Messages} provided by the given
     * {@code iterable}.
     */
    static <M extends Message<?>> MessageStream<M> fromIterable(@Nonnull Iterable<M> iterable) {
        return fromIterable(iterable, message -> Context.empty());
    }

    /**
     * Create a stream that provides the {@link Message Messages} returned by the given {@code iterable}, automatically
     * wrapped in an {@link Entry} with the resulting {@link Context} from the {@code contextSupplier}.
     * <p>
     * Note that each separate consumer of the stream will receive each message if the iterable does so.
     *
     * @param iterable        The {@link Iterable} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code iterable}
     *                        returning the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped
     *                        in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that return the {@link Message Messages} provided by the given
     * {@code iterable} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromIterable(@Nonnull Iterable<M> iterable,
                                                                @Nonnull Function<M, Context> contextSupplier) {
        return new IteratorMessageStream<>(StreamSupport.stream(iterable.spliterator(), false)
                                                        .<Entry<M>>map(message -> new SimpleEntry<>(message,
                                                                                                    contextSupplier.apply(
                                                                                                            message)))
                                                        .iterator());
    }

    /**
     * Create a stream that provides the {@link Message Messages} returned by the given {@code stream}, automatically
     * wrapped in an {@link Entry}.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream The {@link Stream} providing the {@link Message Messages} to stream.
     * @param <M>    The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that return the {@link Message Messages} provided by the given
     * {@code stream}.
     */
    static <M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<M> stream) {
        return fromStream(stream, message -> Context.empty());
    }

    /**
     * Create a stream that provides the {@link Message Messages} returned by the given {@code stream}, automatically
     * wrapped in an {@link Entry} with the resulting {@link Context} from the {@code contextSupplier}.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream          The {@link Stream} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code stream} returning
     *                        the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that return the {@link Message Messages} provided by the given
     * {@code stream} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<M> stream,
                                                              @Nonnull Function<M, Context> contextSupplier) {
        return new IteratorMessageStream<>(stream.map(m -> (Entry<M>) new SimpleEntry<>(m, contextSupplier.apply(m)))
                                                 .iterator());
    }

    /**
     * Create a stream that provides the items of type {@code T} returned by the given {@code stream}, automatically
     * wrapped in an {@link Entry} with the resulting {@link Message} and {@link Context} from the
     * {@code messageSupplier} and the {@code contextSupplier} respectively.
     * <p>
     * Note that each separate consumer of the stream will receive each message of the given {@code stream}, if the
     * stream does so.
     *
     * @param stream          The {@link Stream} providing the items of type {@code T} to map to a {@link Message} and
     *                        {@link Context}.
     * @param messageSupplier A {@link Function} ingesting each item of type {@code T} from the given {@code stream}
     *                        returning the {@link Message} to set for the {@link Entry} to add in the resulting
     *                        MessageStream.
     * @param contextSupplier A {@link Function} ingesting each item of type {@code T} from the given {@code stream}
     *                        returning the {@link Context} to set for the {@link Entry} to add in the resulting
     *                        MessageStream.
     * @param <T>             The type of item contained in the given {@code stream} that will be mapped to a
     *                        {@link Message} and {@link Context} by the {@code messageSupplier} and
     *                        {@code contextSupplier} respectively.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that return the {@link Message Messages} resulting from the given
     * {@code messageSupplier} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <T, M extends Message<?>> MessageStream<M> fromStream(@Nonnull Stream<T> stream,
                                                                 @Nonnull Function<T, M> messageSupplier,
                                                                 @Nonnull Function<T, Context> contextSupplier) {
        return new IteratorMessageStream<>(stream.map(item -> new SimpleEntry<>(messageSupplier.apply(item),
                                                                                contextSupplier.apply(item)))
                                                 .iterator());
    }

    /**
     * Create a stream that provides the {@link Message Messages} returned by the given {@code flux}, automatically
     * wrapped in an {@link Entry}.
     *
     * @param flux The {@link Flux} providing the {@link Message Messages} to stream.
     * @param <M>  The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that returns the {@link Message Messages} provided by the given
     * {@code flux}.
     */
    static <M extends Message<?>> MessageStream<M> fromFlux(@Nonnull Flux<M> flux) {
        return fromFlux(flux, message -> Context.empty());
    }

    /**
     * Create a stream that provides the {@link Message Messages} returned by the given {@code flux}, automatically
     * wrapped in an {@link Entry} with the resulting {@link Context} from the {@code contextSupplier}.
     *
     * @param flux            The {@link Flux} providing the {@link Message Messages} to stream.
     * @param contextSupplier A {@link Function} ingesting each {@link Message} from the given {@code flux} returning
     *                        the {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream of {@link Entry entries} that returns the {@link Message Messages} provided by the given
     * {@code flux} with a {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> MessageStream<M> fromFlux(@Nonnull Flux<M> flux,
                                                            @Nonnull Function<M, Context> contextSupplier) {
        return new FluxMessageStream<>(flux.map(message -> new SimpleEntry<>(message, contextSupplier.apply(message))));
    }

    /**
     * Create a stream that returns a single {@link Entry entry} wrapping the {@link Message} from the given
     * {@code future}, once the given {@code future} completes.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the future returns
     * {@code null}. The stream will complete with an exception when the given {@code future} completes exceptionally.
     *
     * @param future The {@link CompletableFuture} providing the {@link Message} to contain in the stream.
     * @param <M>    The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream containing at most one {@link Entry entry} from the given {@code future}.
     */
    static <M extends Message<?>> Single<M> fromFuture(@Nonnull CompletableFuture<M> future) {
        return fromFuture(future, message -> Context.empty());
    }

    /**
     * Create a stream that returns a single {@link Entry entry} wrapping the {@link Message} from the given
     * {@code future}, once the given {@code future} completes.
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
     * @return A stream containing at most one {@link Entry entry} from the given {@code future} with a {@link Context}
     * provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> Single<M> fromFuture(@Nonnull CompletableFuture<M> future,
                                                       @Nonnull Function<M, Context> contextSupplier) {
        return new SingleValueMessageStream<>(
                future.thenApply(message -> new SimpleEntry<>(message, contextSupplier.apply(message)))
        );
    }

    /**
     * Create a stream containing the single given {@code message}, automatically wrapped in an {@link Entry}.
     * <p>
     * Once the {@code Entry} is consumed, the stream is considered completed.
     *
     * @param message The {@link Message} to wrap in an {@link Entry} and return in the stream.
     * @param <M>     The type of {@link Message} given.
     * @return A stream consisting of a single {@link Entry entry} wrapping the given {@code message}.
     */
    static <M extends Message<?>> Single<M> just(@Nullable M message) {
        return just(message, m -> Context.empty());
    }

    /**
     * Create a stream containing the single given {@code message}, automatically wrapped in an {@link Entry}.
     * <p>
     * Once the {@code Entry} is consumed, the stream is considered completed.
     *
     * @param message         The {@link Message} to wrap in an {@link Entry} and return in the stream.
     * @param contextSupplier A {@link Function} ingesting the given {@code message} returning the {@link Context} to
     *                        set for the {@link Entry} the {@code message} is wrapped in.
     * @param <M>             The type of {@link Message} given.
     * @return A stream consisting of a single {@link Entry entry} wrapping the given {@code message} with a
     * {@link Context} provided by the {@code contextSupplier}.
     */
    static <M extends Message<?>> Single<M> just(@Nullable M message,
                                                 @Nonnull Function<M, Context> contextSupplier) {
        return new SingleValueMessageStream<>(new SimpleEntry<>(message, contextSupplier.apply(message)));
    }

    /**
     * Create a stream that completed with given {@code failure}.
     * <p>
     * All attempts to read from this stream will propagate this error.
     *
     * @param failure The {@link Throwable} to propagate to consumers of the stream.
     * @param <M>     The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream that is completed exceptionally.
     */
    static <M extends Message<?>> Empty<M> failed(@Nonnull Throwable failure) {
        return new FailedMessageStream<>(failure);
    }

    /**
     * Create a stream that carries no {@link Entry} and is considered to be successfully completed. The type of the
     * empty stream is forced to a {@link Message} containing a {@link Void}.
     * <p>
     * To declare empty streams of another type, call {@link #cast()} on the returned {@code Empty} stream.
     * <p>
     * Any attempt to convert this stream to a component that requires an entry to be returned (such as
     * {@link CompletableFuture}), will have it return {@code null}.
     *
     * @return An empty stream.
     */
    static Empty<Message<Void>> empty() {
        return EmptyMessageStream.instance();
    }

    /**
     * Creates a {@link Flux} that consumes the {@link Entry entries} from this stream.
     * <p>
     * The returned {@code Flux} will complete successfully if the stream does so, and exceptionally if the stream
     * completed with an error.
     *
     * @return A {@link Flux} carrying the {@link Entry entries} of this stream.
     */
    default Flux<Entry<M>> asFlux() {
        return MessageStreamUtils.asFlux(this);
    }

    /**
     * Returns a {@link Single stream} that includes only the first message of {@code this} stream, unless it completes
     * without delivering any messages, in which case is completes the same way.
     * <p>
     * When the first message is delivered, the returned stream completes normally, independently of how this stream
     * completes. Upon consuming the first message, this stream is {@link #close()} immediately.
     *
     * @return A {@link Single stream} that includes only the first message of {@code this} stream.
     */
    default Single<M> first() {
        return new TruncateFirstMessageStream<>(this);
    }

    /**
     * Returns a stream that process all messages, but ignores the results and returns an empty stream.
     *
     * @return An Empty stream that ignores all results.
     */
    default Empty<M> ignoreEntries() {
        return new IgnoredEntriesMessageStream<>(this).cast();
    }

    /**
     * Returns an Optional carrying the next {@link Entry entry} from the stream, if such entry was available. If no
     * entry was available for reading, this method returns an empty Optional.
     * <p>
     * This method will never block for elements becoming available.
     *
     * @return An optional carrying the next {@link Entry entry}, if available.
     */
    Optional<Entry<M>> next();

    /**
     * Returns an Optional carrying the next {@link Entry entry} from the stream (without moving the stream pointer), if such entry was available. If no
     * entry was available for reading, this method returns an empty Optional.
     * <p>
     * This method will never block for elements becoming available.
     *
     * @return An optional carrying the next {@link Entry entry}, if available.
     */
    Optional<Entry<M>> peek();

    /**
     * Registers the callback to invoke when {@link Entry entries} are available for reading or when the stream
     * completes (either normally or with an error). An invocation of this method does not in any way guarantee that
     * entries are indeed available, or that the stream has indeed been completed. Implementations may choose to
     * suppress repeated invocations of the callback if no entries have been read in the meantime.
     *
     * @param callback The callback to invoke when {@link Entry entries} are available for reading, or the stream
     *                 completes.
     */
    void onAvailable(@Nonnull Runnable callback);

    /**
     * Indicates whether any error has been reported in this stream. Implementations may choose to not return any error
     * here until all {@link Entry entries} that were available for reading before any error occurred have been
     * consumed.
     *
     * @return An optional containing the possible error this stream completed with.
     */
    Optional<Throwable> error();

    /**
     * Indicates whether this stream has been completed. A completed stream will never return any more
     * {@link Entry entries} from {@link #next()}, and {@link #hasNextAvailable()} will always return {@code false}. If
     * the stream completed with an error, {@link #error()} will report so.
     *
     * @return {@code true} if the stream completed, otherwise {@code false}.
     */
    boolean isCompleted();

    /**
     * Indicates whether an {@link Entry entry} is available for immediate reading. When entries are reported available,
     * there is no guarantee that {@link #next()} will indeed return an entry. However, besides any concurrent activity
     * on this stream, it is guaranteed that no entries are available for reading when this method returns
     * {@code false}.
     *
     * @return {@code true} when there are {@link Entry entries} available for reading, {@code false} otherwise.
     */
    boolean hasNextAvailable();

    /**
     * Closes this stream, freeing any possible resources occupied by the underlying stream. After invocation, some
     * {@link Entry entries} may still be available for reading.
     * <p>
     * Implementations must always release resources when a stream is completed, either with an error or normally.
     * Therefore, it is only required to {@code close()} a stream if the consumer decides to not read until the end.
     */
    void close();

    /**
     * Returns a stream that maps each {@link Entry entry} from this stream using given {@code mapper} function into an
     * entry carrying a {@code MessageEntry} with a {@link Message} of type {@code RM}.
     * <p>
     * The returned stream completes the same way {@code this} stream completes.
     *
     * @param mapper The function converting {@link Entry entries} from this stream from entries containing
     *               {@link Message message} of type {@code M} to {@code RM}.
     * @param <RM>   The declared type of {@link Message} contained in the returned {@link Entry entry}.
     * @return A stream with all {@link Entry entries} mapped according to the {@code mapper} function.
     */
    default <RM extends Message<?>> MessageStream<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        return new MappedMessageStream<>(this, mapper);
    }

    /**
     * Returns a stream that maps each {@link Entry#message() message} from the {@link Entry entries} in this stream
     * using the given {@code mapper} function. This maps the {@link Message Messages} from type {@code M} to type
     * {@code RM}.
     * <p>
     * The returned stream completes the same way {@code this} stream completes.
     *
     * @param mapper The function converting {@link Entry#message() message} from the {@link Entry entries} in this
     *               stream from type {@code M} to {@code RM}.
     * @param <RM>   The declared type of {@link Message} contained in the returned {@link Entry entry}.
     * @return A stream with all {@link Entry entries} mapped according to the {@code mapper} function.
     */
    default <RM extends Message<?>> MessageStream<RM> mapMessage(@Nonnull Function<M, RM> mapper) {
        return map(entry -> entry.map(mapper));
    }

    /**
     * Returns a {@link CompletableFuture} of type {@code R}, using the given {@code identity} as the initial value for
     * the given {@code accumulator}.
     * <p>
     * The {@code accumulator} will process all {@link Entry entries} within this stream until a single value of type
     * {@code R} is left.
     * <p>
     * Note that parallel processing <b>is not</b> supported!
     *
     * @param identity    The initial value given to the {@code accumulator}.
     * @param accumulator The {@link BiFunction} accumulating all {@link Entry entries} from this stream into a return
     *                    value of type {@code R}.
     * @param <R>         The result of the {@code accumulator} after reducing all {@link Entry entries} from this
     *                    stream.
     * @return A {@link CompletableFuture} carrying the result of the given {@code accumulator} that reduced the entire
     * stream.
     */
    default <R> CompletableFuture<R> reduce(@Nonnull R identity, @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return MessageStreamUtils.reduce(this, identity, accumulator);
    }

    /**
     * Invokes the given {@code onNext} each time an {@link Entry entry} is consumed from this stream.
     * <p>
     * Depending on the stream's implementation, the function may be invoked when the entry is provided to the
     * {@link Consumer}, or at the moment it's available for reading on the stream. Subscribing multiple times to the
     * resulting stream may cause the given {@code onNext} to be invoked more than once for an entry.
     *
     * @param onNext The {@link Consumer} to invoke for each {@link Entry entry}.
     * @return A stream that will invoke the given {@code onNext} for each {@link Entry entry}.
     */
    default MessageStream<M> onNext(@Nonnull Consumer<Entry<M>> onNext) {
        return new OnNextMessageStream<>(this, onNext);
    }

    /**
     * Returns a stream that, when {@code this} stream completes with an error, continues reading from the stream
     * provided by given {@code onError} function.
     *
     * @param onError The {@link Function} providing the replacement stream to continue with after an exception on
     *                {@code this} stream.
     * @return A stream that continues onto another stream when {@code this} stream completes with an error.
     */
    default MessageStream<M> onErrorContinue(@Nonnull Function<Throwable, MessageStream<M>> onError) {
        return new OnErrorContinueMessageStream<>(this, onError);
    }

    /**
     * Returns a stream that will filter {@link MessageStream.Entry entries} based on the given {@code filter}.
     *
     * @param filter The {@link MessageStream.Entry} predicate, that will filter out entries. Returning {@code true}
     *               from this lambda will keep the entry, while returning {@code false} will remove it.
     * @return A stream for which the {@link MessageStream.Entry entries} have been filtered by the given
     * {@code filter}.
     */
    default MessageStream<M> filter(@Nonnull Predicate<Entry<M>> filter) {
        return new FilteringMessageStream<>(this, filter);
    }

    /**
     * Returns a stream that concatenates this stream with the given {@code other} stream, if this stream completes
     * successfully.
     * <p>
     * When {@code this} stream completes with an error, so does the returned stream.
     *
     * @param other The MessageStream to append to this stream.
     * @return A stream concatenating this stream with given {@code other}.
     */
    default MessageStream<M> concatWith(@Nonnull MessageStream<M> other) {
        return new ConcatenatingMessageStream<>(this, other);
    }

    /**
     * Returns a stream that invokes the given {@code completeHandler} when the stream completes normally.
     *
     * @param completeHandler The {@link Runnable} to invoke when the stream completes normally.
     * @return A stream that invokes the {@code completeHandler} upon normal completion.
     */
    default MessageStream<M> whenComplete(@Nonnull Runnable completeHandler) {
        return new CompletionCallbackMessageStream<>(this, completeHandler);
    }

    /**
     * Casts this stream to the given type. This method is provided to be more flexible with generics. It is the
     * caller's responsibility to ensure the cast is valid. Failure to do so may result in {@link ClassCastException}
     * when reading elements.
     *
     * @param <T> The type of {@link Message} to cast the MessageStream to.
     * @return This instance, cast to the given {@link Message} of type {@code T}.
     */
    @SuppressWarnings("unchecked")
    default <T extends Message<?>> MessageStream<T> cast() {
        return (MessageStream<T>) this;
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
         * Returns the {@link Message} implementation contained by this Entry.
         *
         * @return The {@link Message} implementation contained by this Entry.
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

        @Override
        <T> Entry<M> withResource(@Nonnull ResourceKey<T> key, @Nonnull T resource);
    }

    /**
     * A {@code MessageStream} implementation that returns at most a <b>single</b> result before completing.
     *
     * @param <M> The type of {@link Message} contained in the singular {@link Entry} of this stream.
     * @author Allard Buijze
     * @author Mateusz Nowak
     * @author Mitchell Herrijgers
     * @author Steven van Beelen
     * @see #fromFuture(CompletableFuture)
     * @see #fromFuture(CompletableFuture, Function)
     * @see #just(Message)
     * @see #just(Message, Function)
     * @since 5.0.0
     */
    interface Single<M extends Message<?>> extends MessageStream<M> {

        @Override
        default Single<M> first() {
            return this;
        }

        @Override
        default <RM extends Message<?>> Single<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
            return new MappedMessageStream.Single<>(this, mapper);
        }

        @Override
        default <RM extends Message<?>> Single<RM> mapMessage(@Nonnull Function<M, RM> mapper) {
            return map(e -> e.map(mapper));
        }

        @Override
        default Single<M> filter(@Nonnull Predicate<Entry<M>> filter) {
            return new FilteringMessageStream.Single<>(this, filter);
        }

        @Override
        default Single<M> onNext(@Nonnull Consumer<Entry<M>> onNext) {
            return new OnNextMessageStream.Single<>(this, onNext);
        }

        @Override
        default Single<M> whenComplete(@Nonnull Runnable completeHandler) {
            return new CompletionCallbackMessageStream.Single<>(this, completeHandler);
        }

        @Override
        default <R extends Message<?>> Single<R> cast() {
            //noinspection unchecked
            return (Single<R>) this;
        }

        /**
         * Returns a {@link CompletableFuture} that completes with the <b>first</b> {@link Entry entry} contained in
         * this {@code MessageStream}, or exceptionally if the stream completes with an error before returning any
         * entries.
         * <p>
         * If the stream completes successfully before returning any entries, the {@code CompletableFuture} completes
         * with a {@code null} value.
         * <p>
         * The underlying stream is {@link #close() closed}  as soon as the first element is returned.
         *
         * @return A {@link CompletableFuture} that completes with the first {@link Entry entry}, {@code null} if it is
         * empty, or exceptionally if the stream propagates an error.
         */
        default CompletableFuture<Entry<M>> asCompletableFuture() {
            return MessageStreamUtils.asCompletableFuture(this);
        }

        /**
         * Creates a {@link Mono} that consumes the singular {@link Entry} from this stream.
         * <p>
         * The returned {@code Mono} will complete successfully if the stream does so, and exceptionally if the stream
         * completed with an error.
         *
         * @return A {@link Mono} carrying the singular {@link Entry} of this stream.
         */
        default Mono<Entry<M>> asMono() {
            return asFlux().singleOrEmpty();
        }
    }

    /**
     * A {@code MessageStream} implementation that completes normally or with an error without returning any elements.
     * <p>
     * Any operations that would {@link #map(Function)} or {@link #reduce(Object, BiFunction)} the stream will do
     * nothing at all for an empty {@code MessageStream}.
     *
     * @param <M> The type of {@link Message} for the empty {@link Entry} of this stream.
     * @author Allard Buijze
     * @author Mateusz Nowak
     * @author Mitchell Herrijgers
     * @author Steven van Beelen
     * @see #empty()
     * @see #failed(Throwable)
     * @since 5.0.0
     */
    interface Empty<M extends Message<?>> extends Single<M> {

        @Override
        default Empty<M> first() {
            return this;
        }

        @Override
        default <RM extends Message<?>> Empty<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
            return cast();
        }

        @Override
        default <RM extends Message<?>> Empty<RM> mapMessage(@Nonnull Function<M, RM> mapper) {
            return cast();
        }

        @Override
        default Empty<M> onNext(@Nonnull Consumer<Entry<M>> onNext) {
            // Empty streams can never have their onNext called.
            return this;
        }

        @Override
        default MessageStream<M> concatWith(@Nonnull MessageStream<M> other) {
            return other;
        }

        @Override
        default Empty<M> whenComplete(@Nonnull Runnable completeHandler) {
            return new CompletionCallbackMessageStream.Empty<>(this, completeHandler);
        }

        @Override
        default <T extends Message<?>> Empty<T> cast() {
            //noinspection unchecked
            return (Empty<T>) this;
        }
    }
}
