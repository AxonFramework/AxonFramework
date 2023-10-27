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

package org.axonframework.eventhandling;

import java.util.Optional;

/**
 * Contract describing a component which is aware of {@link DomainEventMessage} their sequences and is capable of
 * providing the last known sequence number for a given Aggregate identifier.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public interface DomainEventSequenceAware {

    /**
     * Returns the last known sequence number of an Event for the given {@code aggregateIdentifier}.
     * <p>
     * It is preferred to retrieve the last known sequence number from the Domain Event Stream when sourcing an
     * Aggregate from events. However, this method provides an alternative in cases no events have been read. For
     * example when using state storage.
     *
     * @param aggregateIdentifier the identifier of the aggregate to find the highest sequence for
     * @return an optional containing the highest sequence number found, or an empty optional is no events are found
     * for this aggregate
     */
    Optional<Long> lastSequenceNumberFor(String aggregateIdentifier);
}
