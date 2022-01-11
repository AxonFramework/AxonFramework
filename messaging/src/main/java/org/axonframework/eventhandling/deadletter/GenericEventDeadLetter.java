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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.QueueIdentifier;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericEventDeadLetter implements DeadLetterEntry<EventMessage<?>> {

    public static final Throwable SEQUENCED_DEAD_LETTER = null;

    private final QueueIdentifier queueIdentifier;
    private final EventMessage<?> deadLetter;
    private final Throwable failure;
    private final Instant deadLettered;
    private final Instant expiresAt;
    private final Consumer<GenericEventDeadLetter> releaseOperation;

    public GenericEventDeadLetter(QueueIdentifier queueIdentifier,
                                  EventMessage<?> deadLetter,
                                  Instant expiresAt,
                                  Consumer<GenericEventDeadLetter> releaseOperation) {
        this(queueIdentifier, deadLetter, SEQUENCED_DEAD_LETTER, expiresAt, releaseOperation);
    }

    public GenericEventDeadLetter(QueueIdentifier queueIdentifier,
                                  EventMessage<?> deadLetter,
                                  Throwable failure,
                                  Instant expiresAt,
                                  Consumer<GenericEventDeadLetter> releaseOperation) {
        this(queueIdentifier, deadLetter, failure, GenericEventMessage.clock.instant(), expiresAt, releaseOperation);
    }

    public GenericEventDeadLetter(QueueIdentifier queueIdentifier,
                                  EventMessage<?> deadLetter,
                                  Throwable failure,
                                  Instant deadLettered,
                                  Instant expiresAt,
                                  Consumer<GenericEventDeadLetter> releaseOperation) {
        this.queueIdentifier = queueIdentifier;
        this.deadLetter = deadLetter;
        this.failure = failure;
        this.deadLettered = deadLettered;
        this.expiresAt = expiresAt;
        this.releaseOperation = releaseOperation;
    }

    @Override
    public QueueIdentifier queueIdentifier() {
        return this.queueIdentifier;
    }

    @Override
    public EventMessage<?> message() {
        return this.deadLetter;
    }

    @Override
    public Throwable cause() {
        return this.failure;
    }

    @Override
    public Instant deadLettered() {
        return this.deadLettered;
    }

    @Override
    public Instant expiresAt() {
        return this.expiresAt;
    }

    @Override
    public void release() {
        releaseOperation.accept(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GenericEventDeadLetter that = (GenericEventDeadLetter) o;
        return Objects.equals(queueIdentifier, that.queueIdentifier)
                && Objects.equals(deadLetter, that.deadLetter)
                && Objects.equals(failure, that.failure)
                && Objects.equals(deadLettered, that.deadLettered)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueIdentifier, deadLetter, failure, deadLettered, expiresAt);
    }

    @Override
    public String toString() {
        return "GenericEventDeadLetter{" +
                "queueIdentifier=" + queueIdentifier +
                ", deadLetter=" + deadLetter +
                ", failure=" + failure +
                ", deadLettered=" + deadLettered +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
