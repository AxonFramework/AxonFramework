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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic implementation of the {@link DeadLetter} allowing any type of {@link Message} to be dead lettered.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
class GenericDeadLetter<T extends Message<?>> implements DeadLetter<T> {

    private final String identifier;
    private final QueueIdentifier queueIdentifier;
    private final T message;
    private final Throwable cause;
    private Instant expiresAt;
    private final int numberOfRetries;
    private final Instant deadLettered;
    private final Consumer<DeadLetter<T>> acknowledgeOperation;
    private final Consumer<DeadLetter<T>> requeueOperation;

    GenericDeadLetter(DeadLetter<T> letter,
                      Instant expiresAt,
                      Consumer<DeadLetter<T>> acknowledgeOperation,
                      Consumer<DeadLetter<T>> requeueOperation) {
        this(letter.identifier(),
             letter.queueIdentifier(),
             letter.message(),
             letter.cause(),
             letter.deadLettered(),
             expiresAt,
             letter.numberOfRetries() + 1,
             acknowledgeOperation,
             requeueOperation);
    }

    GenericDeadLetter(QueueIdentifier queueIdentifier,
                      T message,
                      Throwable cause,
                      Instant deadLettered,
                      Instant expiresAt,
                      Consumer<DeadLetter<T>> acknowledgeOperation,
                      Consumer<DeadLetter<T>> requeueOperation) {
        this(queueIdentifier, message, cause, deadLettered, expiresAt, 0, acknowledgeOperation, requeueOperation);
    }

    GenericDeadLetter(QueueIdentifier queueIdentifier,
                      T message,
                      Throwable cause,
                      Instant deadLettered,
                      Instant expiresAt,
                      int numberOfRetries,
                      Consumer<DeadLetter<T>> acknowledgeOperation,
                      Consumer<DeadLetter<T>> requeueOperation) {
        this(UUID.randomUUID().toString(), queueIdentifier, message, cause, deadLettered, expiresAt, numberOfRetries,
             acknowledgeOperation, requeueOperation);
    }

    GenericDeadLetter(String identifier,
                      QueueIdentifier queueIdentifier,
                      T message,
                      Throwable cause,
                      Instant deadLettered,
                      Instant expiresAt,
                      int numberOfRetries,
                      Consumer<DeadLetter<T>> acknowledgeOperation,
                      Consumer<DeadLetter<T>> requeueOperation) {
        this.identifier = identifier;
        this.queueIdentifier = queueIdentifier;
        this.message = message;
        this.cause = cause;
        this.deadLettered = deadLettered;
        this.expiresAt = expiresAt;
        this.numberOfRetries = numberOfRetries;
        this.acknowledgeOperation = acknowledgeOperation;
        this.requeueOperation = requeueOperation;
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public QueueIdentifier queueIdentifier() {
        return queueIdentifier;
    }

    @Override
    public T message() {
        return message;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public Instant deadLettered() {
        return deadLettered;
    }

    @Override
    public Instant expiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public int numberOfRetries() {
        return numberOfRetries;
    }

    @Override
    public void acknowledge() {
        acknowledgeOperation.accept(this);
    }

    @Override
    public void requeue() {
        requeueOperation.accept(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        // Check does not include the expiresAt, numberOfRetries, acknowledge,
        //  and requeue operations to allow easy letter removal in the DeadLetterQueue.
        //noinspection unchecked
        GenericDeadLetter<T> that = (GenericDeadLetter<T>) o;
        return Objects.equals(identifier, that.identifier)
                && Objects.equals(queueIdentifier, that.queueIdentifier)
                && Objects.equals(message, that.message)
                && Objects.equals(cause, that.cause)
                && Objects.equals(deadLettered, that.deadLettered);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, queueIdentifier, message, cause, expiresAt, deadLettered, acknowledgeOperation);
    }

    @Override
    public String toString() {
        return "GenericDeadLetter" +
                "identifier=" + queueIdentifier +
                ", queueIdentifier=" + queueIdentifier +
                ", message=" + message +
                ", cause=" + cause +
                ", expiresAt=" + expiresAt +
                ", numberOfRetries=" + numberOfRetries +
                ", deadLettered=" + deadLettered +
                '}';
    }
}
