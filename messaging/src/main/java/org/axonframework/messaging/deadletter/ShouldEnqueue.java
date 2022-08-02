package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * An {@link EnqueueDecision} stating a {@link DeadLetter dead-letter} should be enqueued.
 *
 * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class ShouldEnqueue<D extends DeadLetter<? extends Message<?>>> implements EnqueueDecision<D> {

    private final Throwable enqueueCause;
    private final Function<D, MetaData> diagnosticsBuilder;

    /**
     * Constructs a default {@link EnqueueDecision} to enqueue without any {@link #enqueueCause()}.
     */
    public ShouldEnqueue() {
        this(null);
    }

    /**
     * Constructs a default {@link EnqueueDecision} to enqueue using the given {@code requeueCause} as the
     * {@link #enqueueCause()}.
     *
     * @param enqueueCause The {@link Throwable} that was used to decide to enqueue.
     */
    public ShouldEnqueue(Throwable enqueueCause) {
        this(enqueueCause, d -> MetaData.emptyInstance());
    }

    /**
     * Constructs a default {@link EnqueueDecision} to enqueue using the given {@code requeueCause} as the
     * {@link #enqueueCause()}.
     *
     * @param enqueueCause       The {@link Throwable} that was used to decide to enqueue.
     * @param diagnosticsBuilder A function constructing diagnostics to append during
     *                           {@link #addDiagnostics(DeadLetter)}.
     */
    public ShouldEnqueue(Throwable enqueueCause, Function<D, MetaData> diagnosticsBuilder) {
        this.enqueueCause = enqueueCause;
        this.diagnosticsBuilder = diagnosticsBuilder;
    }

    @Override
    public boolean shouldEvict() {
        return false;
    }

    @Override
    public boolean shouldEnqueue() {
        return true;
    }

    @Override
    public Optional<Throwable> enqueueCause() {
        return Optional.ofNullable(enqueueCause);
    }

    @Override
    public D addDiagnostics(D letter) {
        //noinspection unchecked
        return (D) letter.andDiagnostics(diagnosticsBuilder.apply(letter));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShouldEnqueue<?> that = (ShouldEnqueue<?>) o;
        return Objects.equals(enqueueCause, that.enqueueCause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enqueueCause);
    }

    @Override
    public String toString() {
        return "ShouldEnqueue{" +
                "enqueueCause=" + enqueueCause +
                '}';
    }
}
