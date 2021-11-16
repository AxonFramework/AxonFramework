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
import org.axonframework.messaging.deadletter.DeadLetter;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericEventDeadLetter implements DeadLetter<EventMessage<?>> {

    private final String sequenceIdentifier;
    private final Instant deadLettered;
    private final EventMessage<?> deadLetter;

    /**
     * @param sequenceIdentifier
     * @param deadLetter
     */
    public GenericEventDeadLetter(String sequenceIdentifier,
                                  EventMessage<?> deadLetter) {
        this(sequenceIdentifier, GenericEventMessage.clock.instant(), deadLetter);
    }

    /**
     * @param sequenceIdentifier
     * @param deadLettered
     * @param deadLetter
     */
    public GenericEventDeadLetter(String sequenceIdentifier,
                                  Instant deadLettered,
                                  EventMessage<?> deadLetter) {
        this.sequenceIdentifier = sequenceIdentifier;
        this.deadLettered = deadLettered;
        this.deadLetter = deadLetter;
    }

    @Override
    public String sequenceIdentifier() {
        return sequenceIdentifier;
    }

    @Override
    public Instant deadLettered() {
        return deadLettered;
    }

    @Override
    public EventMessage<?> deadLetter() {
        return deadLetter;
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
        return Objects.equals(sequenceIdentifier, that.sequenceIdentifier) && Objects.equals(
                deadLettered,
                that.deadLettered) && Objects.equals(deadLetter, that.deadLetter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceIdentifier, deadLettered, deadLetter);
    }

    @Override
    public String toString() {
        return "GenericEventDeadLetter{" +
                "sequenceIdentifier='" + sequenceIdentifier + '\'' +
                ", deadLettered=" + deadLettered +
                ", deadLetter=" + deadLetter +
                '}';
    }
}
