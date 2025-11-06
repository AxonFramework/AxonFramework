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

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.sequencing.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * A provider of {@link SequencingPolicy SequencingPolicies} for a given {@link Configuration}.
 * <p>
 * The provided {@code SequencingPolicy} for a given {@code Configuration} returns a sequencing key for an event to
 * identify which sequence/stream the event belongs to. The policy is <b>only</b> used for when a dead letter queue is
 * attached to the processing consuming from a Persistent Stream.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
public class PersistentStreamSequencingPolicyProvider
        implements Function<Configuration, SequencingPolicy> {

    /**
     * A {@link String} constant representing the "sequential per aggregate" sequencing policy. This means all events
     * belonging to the same aggregate are handled sequentially. The behavior of this policy resembles the
     * {@link SequentialPerAggregatePolicy}.
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

    private static final String SEQUENTIAL_POLICY_IDENTIFIER = "SequentialPolicy";

    private final String name;
    private final String sequencingPolicy;
    private final List<String> sequencingPolicyParameters;

    /**
     * Instantiates a {@code PersistentStreamSequencingPolicyProvider} based on the given parameters.
     *
     * @param name                       The processor name.
     * @param sequencingPolicy           The name of the sequencing policy.
     * @param sequencingPolicyParameters Parameters for the sequencing policy.
     */
    public PersistentStreamSequencingPolicyProvider(String name,
                                                    String sequencingPolicy,
                                                    List<String> sequencingPolicyParameters) {
        this.name = name;
        this.sequencingPolicy = sequencingPolicy;
        this.sequencingPolicyParameters = sequencingPolicyParameters;
    }

    @Override
    public SequencingPolicy apply(Configuration configuration) {
        return (event, context) -> Optional.ofNullable(sequencingIdentifier(event));
    }

    private Object sequencingIdentifier(EventMessage event) {
        if (SEQUENTIAL_PER_AGGREGATE_POLICY.equals(sequencingPolicy)) {
            if (event instanceof DomainEventMessage) {
                return ((DomainEventMessage) event).getAggregateIdentifier();
            }
            return event.identifier();
        }

        if (METADATA_SEQUENCING_POLICY.equals(sequencingPolicy)) {
            List<Object> metaDataValues = new LinkedList<>();
            for (String sequencingPolicyParameter : sequencingPolicyParameters) {
                metaDataValues.add(event.metadata().get(sequencingPolicyParameter));
            }
            return metaDataValues;
        }

        if (SEQUENTIAL_POLICY.equals(sequencingPolicy)) {
            return SEQUENTIAL_POLICY_IDENTIFIER;
        }

        if (FULL_CONCURRENCY_POLICY.equals(sequencingPolicy)) {
            return event.identifier();
        }

        if (PROPERTY_SEQUENCING_POLICY.equals(sequencingPolicy)) {
            throw new RuntimeException(
                    name + ": Cannot use the PropertySequencingPolicy in combination with dead-letter queue"
            );
        }

        throw new RuntimeException(format(
                "%s :Unknown sequencing policy %s in combination with dead-letter queue", name, sequencingPolicy
        ));
    }
}
