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

import jakarta.annotation.Nonnull;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a {@link Flux} as the source for {@link Entry entries}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class FluxMessageStream<M extends Message> extends AbstractMessageStream<M> {

    private final Flux<Entry<M>> source;
    private final BlockingQueue<Entry<M>> peeked = new LinkedBlockingQueue<>(5);
    private final AtomicBoolean sourceSubscribed = new AtomicBoolean();
    private final AtomicReference<Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs a {@link MessageStream stream} using the given {@code source} to provide the {@link Entry entries}.
     *
     * @param source The {@link Flux} providing the {@link Entry entries} for this {@link MessageStream stream}.
     */
    FluxMessageStream(@Nonnull Flux<Entry<M>> source) {
        this.source = source;
    }

    @Override
    public <RM extends Message> MessageStream<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        return new FluxMessageStream<>(source.map(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return source.reduce(identity, accumulator).toFuture();
    }

    @Override
    public Optional<Entry<M>> next() {
        subscribeToSource();
        Entry<M> poll = peeked.poll();
        if (poll != null && peeked.isEmpty()) {
            if (!closed.get()) {
                subscription.get().request(1);
            }
        }
        return Optional.ofNullable(poll);
    }

    @Override
    public boolean isCompleted() {
        // Consider the stream completed when the source has completed AND
        // - there is no data left to consume
        // - or an error occurred (in which case we want immediate completion)
        return super.isCompleted() && (peeked.isEmpty() || super.error().isPresent());
    }

    @Override
    public boolean hasNextAvailable() {
        subscribeToSource();
        return !peeked.isEmpty();
    }

    @Override
    public void close() {
        closed.set(true);
        Subscription s = subscription.get();
        if (s != null) {
            s.cancel();
        }
    }

    @Override
    public Optional<Entry<M>> peek() {
        subscribeToSource();
        return Optional.ofNullable(peeked.peek());
    }

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        super.setCallback(callback);
        subscribeToSource();
    }

    private void subscribeToSource() {
        if (!sourceSubscribed.getAndSet(true)) {
            //noinspection ReactiveStreamsSubscriberImplementation
            source.subscribe(new Subscriber<>() {

                @Override
                public void onSubscribe(Subscription s) {
                    subscription.set(s);
                    s.request(1);
                }

                @Override
                public void onNext(Entry<M> mEntry) {
                    peeked.add(mEntry);
                    invokeCallbackSafely();
                    // If the callback failed, clear buffered entries to ensure immediate error propagation
                    if (error().isPresent()) {
                        peeked.clear();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    completeExceptionally(t);
                }

                @Override
                public void onComplete() {
                    complete();
                }
            });
        }
    }

    @Override
    public MessageStream<M> onErrorContinue(@Nonnull Function<Throwable, MessageStream<M>> onError) {
        return new FluxMessageStream<>(source.onErrorResume(exception -> FluxUtils.of(onError.apply(exception))));
    }

}
