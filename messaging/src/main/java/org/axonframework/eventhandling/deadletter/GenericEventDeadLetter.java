/*
 * Copyright (c) 2010-2021. Axon Framework
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

import java.time.Instant;
import java.util.Objects;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericEventDeadLetter implements DeadLetterEntry<EventMessage<?>> {

    public static final Throwable SEQUENCED_DEAD_LETTER = null;

    private final String identifier;
    private final String group;
    private final EventMessage<?> deadLetter;
    private final Throwable failure;
    private final Instant expiresAt;
    private final Instant deadLettered;

    public GenericEventDeadLetter(String identifier,
                                  String group,
                                  EventMessage<?> deadLetter,
                                  Instant expiresAt) {
        this(identifier, group, deadLetter, SEQUENCED_DEAD_LETTER, expiresAt);
    }

    public GenericEventDeadLetter(String identifier,
                                  String group,
                                  EventMessage<?> deadLetter,
                                  Throwable failure,
                                  Instant expiresAt) {
        this(identifier, group, deadLetter, failure, expiresAt, GenericEventMessage.clock.instant());
    }

    public GenericEventDeadLetter(String identifier,
                                  String group,
                                  EventMessage<?> deadLetter,
                                  Throwable failure,
                                  Instant expiresAt,
                                  Instant deadLettered) {
        this.identifier = identifier;
        this.group = group;
        this.deadLetter = deadLetter;
        this.failure = failure;
        this.expiresAt = expiresAt;
        this.deadLettered = deadLettered;
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public EventMessage<?> message() {
        return deadLetter;
    }

    @Override
    public Throwable cause() {
        return failure;
    }

    @Override
    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public Instant deadLettered() {
        return deadLettered;
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
        return Objects.equals(identifier, that.identifier)
                && Objects.equals(group, that.group)
                && Objects.equals(deadLetter, that.deadLetter)
                && Objects.equals(failure, that.failure)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(deadLettered, that.deadLettered);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, group, deadLetter, failure, expiresAt, deadLettered);
    }

    @Override
    public String toString() {
        return "GenericEventDeadLetter{" +
                "identifier='" + identifier + '\'' +
                ", group='" + group + '\'' +
                ", deadLetter=" + deadLetter +
                ", failure=" + failure +
                ", expiresAt=" + expiresAt +
                ", deadLettered=" + deadLettered +
                '}';
    }
}
