package org.axonframework.messaging.core;


import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nonnull;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * A {@link MessageStream} wrapper that carries a Reactor {@link ContextView},
 * restoring it when the stream is consumed asynchronously.
 */
public class ReactorContextMessageStream<M extends Message> implements MessageStream.Single<M> {

    private final MessageStream<M> delegate;
    private final ContextView reactorContext;

    public ReactorContextMessageStream(@Nonnull MessageStream<M> delegate,
                                       @Nonnull ContextView reactorContext) {
        this.delegate = delegate;
        this.reactorContext = reactorContext;
    }

    public ContextView getReactorContext() {
        return reactorContext;
    }

    // ── Delegate all MessageStream methods ──────────────────────

    @Override
    public Optional<Entry<M>> next() {
        return delegate.next();
    }

    @Override
    public Optional<Entry<M>> peek() {
        return delegate.peek();
    }

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        delegate.setCallback(callback);
    }

    @Override
    public Optional<Throwable> error() {
        return delegate.error();
    }

    @Override
    public boolean isCompleted() {
        return delegate.isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return delegate.hasNextAvailable();
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ── Key override: restore Reactor Context on async consumption ──

    @Override
    public CompletableFuture<Entry<M>> asCompletableFuture() {
        // Subscribes inside the captured Reactor Context
        return Mono.fromFuture(MessageStreamUtils.asCompletableFuture(delegate))
                   .contextWrite(reactorContext)
                   .toFuture();
    }
}
