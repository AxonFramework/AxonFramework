/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link DeadLetter dead letter} allowing any type of {@link Message} to be dead
 * lettered.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class GenericDeadLetter<M extends Message<?>> implements DeadLetter<M> {

    /**
     * {@link Clock} instance used to set the {@link DeadLetter#enqueuedAt()} and {@link DeadLetter#lastTouched()} times
     * on {@link DeadLetter dead letters}. Can be adjusted to alter the desired time(zone) of those fields.
     */
    public static Clock clock = Clock.systemUTC();

    private final Object sequenceIdentifier;
    private final M message;
    private final Cause cause;
    private final Instant enqueuedAt;
    private final Instant lastTouched;
    private final MetaData diagnostics;

    /**
     * Construct a {@link GenericDeadLetter} with the given {@code sequenceIdentifier} and {@code message}. The
     * {@link #cause()} is left empty in this case. This method is typically used to construct a dead letter that's part
     * of a sequence.
     *
     * @param sequenceIdentifier The sequence identifier of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     */
    public GenericDeadLetter(Object sequenceIdentifier, M message) {
        this(sequenceIdentifier, message, null);
    }

    /**
     * Construct a {@link GenericDeadLetter} with the given {@code sequenceIdentifier}, {@code message}, and
     * {@code cause}. This method is typically used to construct the first dead letter entry for the given
     * {@code queueIdentifier}.
     *
     * @param sequenceIdentifier The sequence identifier of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     * @param cause              The cause for the {@code message} to be dead lettered.
     */
    public GenericDeadLetter(Object sequenceIdentifier, M message, Throwable cause) {
        this(sequenceIdentifier,
             message,
             cause != null ? new ThrowableCause(cause) : null,
             () -> clock.instant());
    }

    private GenericDeadLetter(Object sequenceIdentifier,
                              M message,
                              Cause cause,
                              Supplier<Instant> timeSupplier) {
        this(sequenceIdentifier,
             message,
             cause,
             timeSupplier.get(),
             timeSupplier.get(),
             MetaData.emptyInstance());
    }

    private GenericDeadLetter(GenericDeadLetter<M> delegate, Instant touched) {
        this(delegate.sequenceIdentifier,
             delegate.message(),
             delegate.cause().orElse(null),
             delegate.enqueuedAt(),
             touched,
             delegate.diagnostics);
    }

    private GenericDeadLetter(GenericDeadLetter<M> delegate, MetaData diagnostics) {
        this(delegate.sequenceIdentifier,
             delegate.message(),
             delegate.cause().orElse(null),
             delegate.enqueuedAt(),
             clock.instant(),
             diagnostics);
    }

    private GenericDeadLetter(GenericDeadLetter<M> delegate, Throwable requeueCause) {
        this(delegate.sequenceIdentifier,
             delegate.message(),
             requeueCause != null ? new ThrowableCause(requeueCause) : delegate.cause,
             delegate.enqueuedAt(),
             clock.instant(),
             delegate.diagnostics());
    }

    /**
     * Construct a {@link GenericDeadLetter} defining all the fields.
     *
     * @param sequenceIdentifier The sequence identifier of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     * @param cause              The cause for the {@code message} to be dead lettered.
     * @param enqueuedAt         The moment this dead letter is enqueued.
     * @param lastTouched        The last time this dead letter was touched.
     * @param diagnostics        The diagnostic {@link MetaData} of this dead letter.
     */
    public GenericDeadLetter(Object sequenceIdentifier,
                             M message,
                             Cause cause,
                             Instant enqueuedAt,
                             Instant lastTouched,
                             MetaData diagnostics) {
        this.sequenceIdentifier = sequenceIdentifier;
        this.message = message;
        this.cause = cause;
        this.enqueuedAt = enqueuedAt;
        this.lastTouched = lastTouched;
        this.diagnostics = diagnostics;
    }

    @Override
    public M message() {
        return message;
    }

    @Override
    public Optional<Cause> cause() {
        return Optional.ofNullable(cause);
    }

    @Override
    public Instant enqueuedAt() {
        return enqueuedAt;
    }

    @Override
    public Instant lastTouched() {
        return lastTouched;
    }

    @Override
    public MetaData diagnostics() {
        return diagnostics;
    }

    public DeadLetter<M> markTouched() {
        return new GenericDeadLetter<>(this, clock.instant());
    }

    @Override
    public DeadLetter<M> withCause(@Nonnull Throwable requeueCause) {
        return new GenericDeadLetter<>(this, requeueCause);
    }

    @Override
    public DeadLetter<M> withDiagnostics(MetaData diagnostics) {
        return new GenericDeadLetter<>(this, diagnostics);
    }

    /**
     * Returns the sequence identifier of the sequence this {@link #message()} belongs to.
     *
     * @return The sequence identifier of the sequence this {@link #message()} belongs to.
     */
    public Object getSequenceIdentifier() {
        return sequenceIdentifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GenericDeadLetter<?> that = (GenericDeadLetter<?>) o;
        return Objects.equals(sequenceIdentifier, that.sequenceIdentifier)
                && Objects.equals(message, that.message)
                && Objects.equals(cause, that.cause)
                && Objects.equals(enqueuedAt, that.enqueuedAt)
                && Objects.equals(lastTouched, that.lastTouched)
                && Objects.equals(diagnostics, that.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceIdentifier, message, cause, enqueuedAt, lastTouched, diagnostics);
    }

    @Override
    public String toString() {
        return "GenericDeadLetter{" +
                "sequenceIdentifier=" + sequenceIdentifier +
                ", message=" + message +
                ", cause=" + cause +
                ", enqueuedAt=" + enqueuedAt +
                ", lastTouched=" + lastTouched +
                ", diagnostics=" + diagnostics +
                '}';
    }
}
