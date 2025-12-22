/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.messaging.eventhandling.sequencing.FullConcurrencyPolicy;
import org.axonframework.messaging.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;

/**
 * A constant names of {@link SequencingPolicy SequencingPolicies} available in Persistent Streams.
 * <p>
 *
 * @author Marc Gathier
 * @author Mateusz Nowak
 * @since 4.10.0
 */
public class PersistentStreamSequencingPolicies {

    /**
     * A {@link String} constant representing the "sequential per aggregate" sequencing policy. This means all events
     * belonging to the same aggregate are handled sequentially. The behavior of this policy resembles the
     * {@link SequentialPerAggregatePolicy}.
     * <p>
     * <b>Note:</b> This policy is only applicable for non-DCB (legacy aggregate-based) contexts.
     * In DCB contexts, events do not have aggregate identifiers, so this policy will not work as expected.
     */
    public static final String SEQUENTIAL_PER_AGGREGATE_POLICY = "SequentialPerAggregatePolicy";

    /**
     * A {@link String} constant representing the "metadata" sequencing policy. The policy utilizes values present in
     * the metadata of an event to define the sequence identifier.
     */
    public static final String METADATA_SEQUENCING_POLICY = "MetadataSequencingPolicy";

    /**
     * A {@link String} constant representing the sequential policy. This means all events are handled sequentially. The
     * behavior of this policy resembles the {@link SequentialPolicy}.
     */
    public static final String SEQUENTIAL_POLICY = "SequentialPolicy";

    /**
     * A {@link String} constant representing the full concurrency policy. This means all events are spread out over the
     * available segments, regardless of the sequence identifier. The behavior of this policy resembles the
     * {@link FullConcurrencyPolicy}.
     */
    public static final String FULL_CONCURRENCY_POLICY = "FullConcurrencyPolicy";

    /**
     * A {@link String} constant representing the property sequencing policy. This policy retrieves a value from the
     * event's payload to decide the sequence identifier of the event. The behavior of this policy resembles the
     * {@link PropertySequencingPolicy}.
     */
    public static final String PROPERTY_SEQUENCING_POLICY = "PropertySequencingPolicy";
}
