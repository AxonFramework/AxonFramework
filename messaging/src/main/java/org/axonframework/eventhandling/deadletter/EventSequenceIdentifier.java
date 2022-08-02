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


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.messaging.deadletter.SequenceIdentifier;

import java.beans.ConstructorProperties;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Implementation of the {@link SequenceIdentifier} dedicated for dead-lettering in event handling components.
 * <p>
 * This identifier is used to uniquely identify a sequence of events for a specific {@code processingGroup}. The
 * sequence identifier is typically the result of a
 * {@link org.axonframework.eventhandling.async.SequencingPolicy#getSequenceIdentifierFor(Object)} operation.
 *
 * @author Steven van Beelen
 * @see DeadLetteringEventHandlerInvoker
 * @since 4.6.0
 */
public class EventSequenceIdentifier implements SequenceIdentifier {

    private static final long serialVersionUID = 5879495420316225726L;

    private final Object sequenceIdentifier;
    private final String processingGroup;

    /**
     * Constructs an event handling specific {@link SequenceIdentifier}.
     *
     * @param sequenceIdentifier The identifier of a sequence of events to enqueue.
     * @param processingGroup    The processing group that is required to enqueue events.
     */
    @JsonCreator
    @ConstructorProperties({"identifier", "group"})
    public EventSequenceIdentifier(@JsonProperty("identifier") @Nonnull Object sequenceIdentifier,
                                   @JsonProperty("group") @Nonnull String processingGroup) {
        this.sequenceIdentifier = sequenceIdentifier;
        this.processingGroup = processingGroup;
    }

    @JsonGetter
    @Override
    public Object identifier() {
        return this.sequenceIdentifier;
    }

    @JsonGetter
    @Override
    public String group() {
        return this.processingGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventSequenceIdentifier that = (EventSequenceIdentifier) o;
        return Objects.equals(sequenceIdentifier, that.sequenceIdentifier)
                && Objects.equals(processingGroup, that.processingGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceIdentifier, processingGroup);
    }

    @Override
    public String toString() {
        return "EventSequencedIdentifier{" +
                "sequenceIdentifier=" + sequenceIdentifier +
                ", processingGroup='" + processingGroup + '\'' +
                '}';
    }
}
