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

package org.axonframework.eventhandling.deadletter.jpa;

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
 * A {@link DeadLetter} that was saved to the database and reconstructed from it.
 *
 * @param <M> The {@link EventMessage} type of the contained message.
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class JpaDeadLetter<M extends EventMessage<?>> implements DeadLetter<M> {

    private final String id;
    private final Long index;
    private final String sequence;
    private final Instant enqueuedAt;
    private final Instant lastTouched;
    private final Cause cause;
    private final MetaData diagnostics;
    private final M message;

    /**
     * Constructs a new {@link JpaDeadLetter} from a {@link DeadLetterEntry}, deserialized diagnostics and a
     * reconstructed message.
     */
    public JpaDeadLetter(DeadLetterEntry entry, MetaData diagnostics, M message) {
        this.id = entry.getDeadLetterId();
        this.index = entry.getIndex();
        this.enqueuedAt = entry.getEnqueuedAt();
        this.lastTouched = entry.getLastTouched();
        this.sequence = entry.getSequence();
        if (entry.getCauseType() != null) {
            cause = new ThrowableCause(entry.getCauseType(), entry.getCauseMessage());
        } else {
            cause = null;
        }
        this.diagnostics = diagnostics;
        this.message = message;
    }

    /**
     * Constructs a new {@link JpaDeadLetter} from all possible properties. This is called by itself with changed since
     * this {@link DeadLetter} is immutable.
     */
    JpaDeadLetter(String id,
                  Long index,
                  String sequence,
                  Instant enqueuedAt,
                  Instant lastTouched,
                  Cause cause,
                  MetaData diagnostics,
                  M message) {
        this.id = id;
        this.index = index;
        this.sequence = sequence;
        this.enqueuedAt = enqueuedAt;
        this.lastTouched = lastTouched;
        this.cause = cause;
        this.diagnostics = diagnostics;
        this.message = message;
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

    public String getId() {
        return id;
    }

    public Long getIndex() {
        return index;
    }

    public String getSequence() {
        return sequence;
    }


    @Override
    public DeadLetter<M> markTouched() {
        return new JpaDeadLetter<>(id,
                                   index,
                                   sequence,
                                   enqueuedAt,
                                   GenericDeadLetter.clock.instant(),
                                   cause,
                                   diagnostics,
                                   message);
    }

    @Override
    public DeadLetter<M> withCause(Throwable requeueCause) {
        return new JpaDeadLetter<>(id,
                                   index, sequence,
                                   enqueuedAt,
                                   lastTouched,
                                   requeueCause != null ? new ThrowableCause(requeueCause) : cause,
                                   diagnostics,
                                   message);
    }

    @Override
    public DeadLetter<M> withDiagnostics(MetaData diagnostics) {
        return new JpaDeadLetter<>(id, index, sequence, enqueuedAt, lastTouched, cause, diagnostics, message);
    }

    @Override
    public String toString() {
        return "JpaDeadLetter{" +
                "id='" + id + "'" +
                ", index=" + index +
                ", sequence='" + sequence + "'" +
                ", enqueuedAt=" + enqueuedAt +
                ", lastTouched=" + lastTouched +
                ", cause=" + cause +
                ", diagnostics=" + diagnostics +
                ", message=" + message +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JpaDeadLetter<?> that = (JpaDeadLetter<?>) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (!index.equals(that.index)) {
            return false;
        }
        if (!sequence.equals(that.sequence)) {
            return false;
        }
        if (!enqueuedAt.equals(that.enqueuedAt)) {
            return false;
        }
        if (!lastTouched.equals(that.lastTouched)) {
            return false;
        }
        if (!Objects.equals(cause, that.cause)) {
            return false;
        }
        if (!Objects.equals(diagnostics, that.diagnostics)) {
            return false;
        }
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + index.hashCode();
        result = 31 * result + sequence.hashCode();
        result = 31 * result + enqueuedAt.hashCode();
        result = 31 * result + lastTouched.hashCode();
        result = 31 * result + (cause != null ? cause.hashCode() : 0);
        result = 31 * result + (diagnostics != null ? diagnostics.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }
}
