/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.ThrowableCause;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link DeadLetter} that was saved to a JDBC-backed database and reconstructed from it. This object is immutable and
 * should only be changed using the {@link #withCause(Throwable)}, {@link #withDiagnostics(MetaData)} and
 * {@link #markTouched()} functions. These reconstruct a new object with the specified new properties.
 *
 * @param <E> The {@link EventMessage} contained in this {@link DeadLetter}.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 4.8.0
 */
public class JdbcDeadLetter<E extends EventMessage<?>> implements DeadLetter<E> {

    private final String identifier;
    private final long sequenceIndex;
    private final String sequenceIdentifier;
    private final Instant enqueuedAt;
    private final Instant lastTouched;
    private final Cause cause;
    private final MetaData diagnostics;
    private final E message;

    /**
     * Constructs a new {@link JdbcDeadLetter} with all possible parameters.
     *
     * @param identifier         The identifier of the dead letter.
     * @param index              The index of the dead letter.
     * @param sequenceIdentifier The sequence identifier of the dead letter.
     * @param enqueuedAt         The time the letter was enqueued.
     * @param lastTouched        The time the letter was last touched.
     * @param cause              The cause of enqueueing, can be null if it was queued because there was another letter
     *                           in the same sequence (based on the {@code sequenceIdentifier}).
     * @param diagnostics        The diagnostics provided during enqueueing.
     * @param message            The message that was enqueued.
     */
    public JdbcDeadLetter(String identifier,
                          long index,
                          String sequenceIdentifier,
                          Instant enqueuedAt,
                          Instant lastTouched,
                          Cause cause,
                          MetaData diagnostics,
                          E message) {
        this.identifier = identifier;
        this.sequenceIndex = index;
        this.sequenceIdentifier = sequenceIdentifier;
        this.enqueuedAt = enqueuedAt;
        this.lastTouched = lastTouched;
        this.cause = cause;
        this.diagnostics = diagnostics;
        this.message = message;
    }

    @Override
    public E message() {
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

    /**
     * The identifier of this letter's database entry.
     *
     * @return The identifier of this {@link JdbcDeadLetter}.
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The index of this dead letter within its sequence, identified by the {@code sequenceIdentifier}. The index
     * ensures the events are kept in the original order.
     *
     * @return The index of this {@link JdbcDeadLetter}.
     */
    public long getSequenceIndex() {
        return sequenceIndex;
    }

    /**
     * The sequence identifier of this {@link DeadLetter}. If letters belong to the same sequence, they should be
     * handled sequentially at all times. This ordering is maintained through the {@link #getSequenceIndex()} property.
     *
     * @return The sequence identifier of this {@link DeadLetter}.
     */
    public String getSequenceIdentifier() {
        return sequenceIdentifier;
    }

    @Override
    public DeadLetter<E> markTouched() {
        return new JdbcDeadLetter<>(identifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    GenericDeadLetter.clock.instant(),
                                    cause,
                                    diagnostics,
                                    message);
    }

    @Override
    public DeadLetter<E> withCause(Throwable requeueCause) {
        return new JdbcDeadLetter<>(identifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    GenericDeadLetter.clock.instant(),
                                    requeueCause != null ? new ThrowableCause(requeueCause) : cause,
                                    diagnostics,
                                    message);
    }

    @Override
    public DeadLetter<E> withDiagnostics(MetaData diagnostics) {
        return new JdbcDeadLetter<>(identifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    GenericDeadLetter.clock.instant(),
                                    cause,
                                    diagnostics,
                                    message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JdbcDeadLetter<?> that = (JdbcDeadLetter<?>) o;
        return Objects.equals(identifier, that.identifier)
                && Objects.equals(sequenceIndex, that.sequenceIndex)
                && Objects.equals(sequenceIdentifier, that.sequenceIdentifier)
                && Objects.equals(enqueuedAt, that.enqueuedAt)
                && Objects.equals(lastTouched, that.lastTouched)
                && Objects.equals(cause, that.cause)
                && Objects.equals(diagnostics, that.diagnostics)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier,
                            sequenceIndex,
                            sequenceIdentifier,
                            enqueuedAt,
                            lastTouched,
                            cause,
                            diagnostics,
                            message);
    }

    @Override
    public String toString() {
        return "JdbcDeadLetter{" +
                "identifier='" + identifier + '\'' +
                ", sequenceIndex=" + sequenceIndex +
                ", sequenceIdentifier='" + sequenceIdentifier + '\'' +
                ", enqueuedAt=" + enqueuedAt +
                ", lastTouched=" + lastTouched +
                ", cause=" + cause +
                ", diagnostics=" + diagnostics +
                ", message=" + message +
                '}';
    }
}
