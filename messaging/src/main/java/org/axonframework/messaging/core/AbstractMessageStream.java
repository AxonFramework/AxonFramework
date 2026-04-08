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

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.MessageStream.Entry;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static org.axonframework.messaging.core.MessageStreamUtils.NO_OP_CALLBACK;

/**
 * Abstract base implementation of {@link MessageStream} that provides full state management for
 * consumption, completion, error propagation, and callback coordination.
 * <p>
 * This implementation enforces a non-blocking, pull-based consumption model with callback-driven
 * signaling. Consumers invoke {@link #next()} to attempt to retrieve entries. When no entry is
 * available at that time, the stream may enter an <i>awaiting data</i> state. In this state,
 * the stream expects an external signal indicating that progress may be possible again.
 * <p>
 * Implementations must provide data through {@link #fetchNext()}, which returns a
 * {@link FetchResult} describing the current availability:
 * <ul>
 *     <li>{@link FetchResult.Value} - an entry is available</li>
 *     <li>{@link FetchResult.NotReady} - no entry is currently available, but more may arrive</li>
 *     <li>{@link FetchResult.Completed} - the stream is exhausted and will produce no further entries</li>
 *     <li>{@link FetchResult.Error} - the stream has failed with an error</li>
 * </ul>
 * <p>
 * When {@link FetchResult.NotReady} is returned, this implementation marks the stream as
 * <i>awaiting data</i>. Implementations are expected to invoke {@link #signalProgress()} when
 * new data may be available, or when the stream completes or fails asynchronously. This will
 * trigger the registered callback if the consumer is awaiting data.
 * <p>
 * The registered callback (see {@link #setCallback(Runnable)}) is invoked when:
 * <ul>
 *     <li>entries may be available for consumption,</li>
 *     <li>the stream completes (normally or exceptionally)</li>
 * </ul>
 * Invocation of the callback does not guarantee that entries are available; consumers must
 * always re-check the stream using {@link #next()}, {@link #peek()}, {@link #isCompleted()},
 * and {@link #error()}.
 * <p>
 * This class is thread-safe; all state transitions are guarded to support concurrent interaction
 * between producers and consumers.
 * <p>
 * All methods in this class are either {@code final}, {@code private}, {@code abstract} or empty to
 * protect its invariants.
 *
 * @param <M> The type of {@link Message} contained in the {@link MessageStream.Entry entries} of this stream.
 * @author Jan Galinski
 * @author John Hendrikx
 * @since 5.1.0
 */
@Internal
public abstract class AbstractMessageStream<M extends Message> implements MessageStream<M> {

    /**
     * Represents the result of attempting to fetch the next element from a
     * {@link MessageStream}.
     * <p>
     * A {@code FetchResult} models four distinct outcomes:
     * <ul>
     *     <li>{@link Value} – an element is available and returned</li>
     *     <li>{@link Completed} – no element is available and no further elements will ever arrive</li>
     *     <li>{@link NotReady} – no element is available at present, but more may become available later</li>
     *     <li>{@link Error} – the stream has failed with an error</li>
     * </ul>
     * <p>
     * This abstraction allows implementations to distinguish between a stream that is
     * temporarily out of elements and one that has been fully exhausted.
     *
     * @param <T> The type of value returned when available
     */
    public sealed interface FetchResult<T extends Entry<?>> {

        /**
         * Creates a {@link FetchResult} representing a successfully fetched value.
         *
         * @param <T> the entry type
         * @param value the non-{@code null} value
         * @return a {@link Value} containing the given value, never {@code null}
         */
        static <T extends Entry<?>> FetchResult<T> of(T value) {
            return new Value<>(value);
        }

        /**
         * Creates a {@link FetchResult} representing a producer side error.
         *
         * @param <T> the entry type
         * @param error the non-{@code null} error
         * @return an {@link Error} representing the failure, never {@code null}
         */
        static <T extends Entry<?>> FetchResult<T> error(Throwable error) {
            return new Error<>(error);
        }

        /**
         * Returns a {@link FetchResult} indicating that no element is available and
         * no further elements will be produced.
         *
         * @param <T> the entry type
         * @return an {@link Completed} result
         */
        @SuppressWarnings("unchecked")
        static <T extends Entry<?>> FetchResult<T> completed() {
            return (FetchResult<T>) Completed.INSTANCE;
        }

        /**
         * Returns a {@link FetchResult} indicating that no element is currently available,
         * but more elements may become available in the future.
         *
         * @param <T> the entry type
         * @return a {@link NotReady} result
         */
        @SuppressWarnings("unchecked")
        static <T extends Entry<?>> FetchResult<T> notReady() {
            return (FetchResult<T>) NotReady.INSTANCE;
        }

        /**
         * A {@link FetchResult} containing a successfully fetched value.
         *
         * @param <T> the entry type
         * @param value the non-{@code null} value
         */
        record Value<T extends Entry<?>>(T value) implements FetchResult<T> {
            public Value {
                Objects.requireNonNull(value, "value");
            }
        }

        /**
         * A {@link FetchResult} representing a terminal error in the stream.
         *
         * @param <T> the entry type
         * @param error the non-{@code null} error
         */
        record Error<T extends Entry<?>>(Throwable error) implements FetchResult<T> {
            public Error {
                Objects.requireNonNull(error, "error");
            }
        }

        /**
         * A {@link FetchResult} indicating that the stream is exhausted and no further
         * elements will be produced.
         *
         * @param <T> the entry type
         */
        record Completed<T extends Entry<?>>() implements FetchResult<T> {
            private static final Completed<?> INSTANCE = new Completed<>();
        }

        /**
         * A {@link FetchResult} indicating that no element is currently available,
         * but the stream may produce more elements in the future.
         *
         * @param <T> the entry type
         */
        record NotReady<T extends Entry<?>>() implements FetchResult<T> {
            private static final NotReady<?> INSTANCE = new NotReady<>();
        }
    }

    /**
     * Holds the {@link Runnable} that is called when the stream completes or items
     * are available for reading.
     */
    private Runnable callback = NO_OP_CALLBACK;

    /**
     * Contains the entry that was peeked because of a call to {@link #peek()} or {@link #hasNextAvailable()}.
     * A call to {@link #next()} will return this entry and clear it.
     */
    @Nullable
    private Entry<M> peekedEntry;

    /**
     * Holds the error if the stream completed with an error. This must be {@code null}
     * if completed is {@code false}.
     */
    @Nullable
    private Throwable error;

    /**
     * Indicates that this stream is completed, either because all items were consumed
     * and no more can become available, or because an error occurred.
     */
    private boolean completed;

    /**
     * Indicates that the stream was fully consumed, and the consumer must be notified
     * via the callback to trigger further consumption. When this flag is {@code false}
     * external triggers that would normally lead to a callback are supressed.
     */
    private boolean awaitingData;

    /**
     * Creates a new {@link AbstractMessageStream} in a not-ready state.
     * The stream will initially be marked as {@code awaitingData} until
     * {@link #signalProgress()} or {@link #fetchNext()} indicates new entries
     * or completion.
     */
    public AbstractMessageStream() {
        this(FetchResult.notReady());
    }

    /**
     * Creates a new {@link AbstractMessageStream} with an initial {@link FetchResult}.
     * <p>
     * The behavior depends on the type of {@code fetchResult}:
     * <ul>
     *   <li>{@link FetchResult.Value} - <b>not allowed</b>: throws IllegalArgumentException.</li>
     *   <li>{@link FetchResult.Error} - completes the stream exceptionally.</li>
     *   <li>{@link FetchResult.Completed} - marks the stream as completed immediately.</li>
     *   <li>{@link FetchResult.NotReady} - marks the stream as {@code awaitingData} until entries arrive.</li>
     * </ul>
     *
     * @param fetchResult the initial fetch result; must not be null and must not be a Value
     * @throws NullPointerException if {@code fetchResult} is null
     * @throws IllegalArgumentException if {@code fetchResult} is a Value
     */
    public AbstractMessageStream(FetchResult<Entry<M>> fetchResult) {
        Objects.requireNonNull(fetchResult, "The fetchResult parameter cannot be null.");

        switch(fetchResult) {
            case FetchResult.Value(Entry<M> value) -> throw new IllegalArgumentException("Value fetchResult not supported at construction time");
            case FetchResult.Error(Throwable error) -> completeExceptionally(error);
            case FetchResult.Completed() -> complete();
            case FetchResult.NotReady() -> awaitingData = true;
        }
    }

    @Override
    public final synchronized void setCallback(Runnable callback) {
        Objects.requireNonNull(callback, "The callback parameter cannot be null.");

        this.callback = callback;

        if (hasNextAvailable() || isCompleted()) {
            invokeCallbackSafely();
        }
    }

    /**
     * Signals that the stream may have made progress.
     * <p>
     * This method should be invoked by implementations when an external event occurs that may
     * allow the consumer to make progress. This includes, but is not limited to:
     * <ul>
     *     <li>new entries becoming available,</li>
     *     <li>the stream completing normally, or</li>
     *     <li>the stream failing with an error.</li>
     * </ul>
     * <p>
     * If the stream is currently awaiting data (i.e., a previous call to {@link #next()} returned
     * no entry due to {@link FetchResult.NotReady}), this method invokes the registered callback.
     * Otherwise, this method has no effect.
     * <p>
     * The callback is invoked in a safe manner: any exception thrown by the callback will cause
     * the stream to complete exceptionally with that error.
     */
    protected final synchronized void signalProgress() {

        /*
         * External progress signals should only be honored if the stream is currently
         * in a state where it is awaiting data.
         */

        if (awaitingData) {
            invokeCallbackSafely();
        }
    }

    private Throwable invokeCallbackSafely() {
        try {
            callback.run();

            return null;
        }
        catch (Throwable t) {
            if (error == null) {
                this.error = t;
                this.completed = true;
            }

            return t;
        }
    }

    @Override
    public final synchronized Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public final synchronized boolean isCompleted() {
        return completed;
    }

    @Override
    public final synchronized boolean hasNextAvailable() {
        return peek().isPresent();
    }

    @Override
    public final synchronized Optional<Entry<M>> peek() {
        if (completed) {
            return Optional.empty();
        }

        if (peekedEntry == null) {
            peekedEntry = next().orElse(null);
        }

        return Optional.ofNullable(peekedEntry);
    }

    @Override
    public final synchronized Optional<Entry<M>> next() {
        if (completed) {
            return Optional.empty();
        }

        if (peekedEntry != null) {
            Entry<M> value = peekedEntry;

            this.peekedEntry = null;

            return Optional.of(value);
        }

        this.awaitingData = false;

        return switch(fetchNext()) {
            case FetchResult.Value(Entry<M> v) -> Optional.of(v);
            case FetchResult.NotReady() -> {
                awaitingData = true;

                yield Optional.empty();
            }
            case FetchResult.Error(Throwable error) -> {
                completeExceptionally(error);

                yield Optional.empty();
            }
            case FetchResult.Completed() -> {
                complete();

                yield Optional.empty();
            }
        };
    }

    /**
     * Attempts to fetch the next available {@link Entry} from the underlying source.
     * <p>
     * This method is invoked by {@link #next()} when no previously peeked entry is available.
     * Implementations must return a {@link FetchResult} describing the current state of the stream:
     * <ul>
     *     <li>{@link FetchResult.Value} if an entry is immediately available,</li>
     *     <li>{@link FetchResult.NotReady} if no entry is currently available but more may arrive later,</li>
     *     <li>{@link FetchResult.Completed} if the stream is exhausted and will produce no further entries,</li>
     *     <li>{@link FetchResult.Error} if the stream has failed with an error.</li>
     * </ul>
     * <p>
     * Returning {@link FetchResult.NotReady} will transition the stream into an <i>awaiting data</i>
     * state. Implementations must subsequently invoke {@link #signalProgress()} when progress may
     * be possible again (e.g., when new data arrives or the stream completes).
     * <p>
     * This method must be non-blocking. It should return immediately with the best available
     * information about the stream's current state.
     *
     * @return a {@link FetchResult} representing the outcome of the fetch attempt
     */
    protected abstract FetchResult<Entry<M>> fetchNext();

    /**
     * Callback invoked when the stream transitions to a completed state, either
     * successfully or exceptionally. Subclasses may override this method to
     * perform custom actions on completion.
     */
    protected void onCompleted() {
        // leave empty, don't add behavior that then must rely on a super() call!
    }

    private void complete() {  // keep private to guard this class's invariants
        if (!completed) {
            this.completed = true;

            onCompleted();
            invokeCallbackSafely();
        }
    }

    private void completeExceptionally(Throwable throwable) {  // keep private to guard this class's invariants
        if (error == null) {
            this.error = throwable;
            this.completed = true;

            onCompleted();
            invokeCallbackSafely();
        }
    }
}
