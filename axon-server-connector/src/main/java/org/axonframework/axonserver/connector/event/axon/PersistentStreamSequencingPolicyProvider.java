/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * A provider of {@link SequencingPolicy SequencingPolicies} for a given {@link Configuration}.
 * <p>
 * The provided {@code SequencingPolicy} for a given {@code Configuration} returns a sequencing key for an event to
 * identify which sequence/stream the event belongs to. The policy is <b>only</b> used for when a dead letter queue is
 * attached to the processors consuming from a Persistent Stream.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
public class PersistentStreamSequencingPolicyProvider
        implements Function<Configuration, SequencingPolicy<? super EventMessage<?>>> {

    /**
     * A {@link String} constant representing the "sequential per aggregate" sequencing policy. This means all events
     * belonging to the same aggregate are handled sequentially. The behavior of this policy resembles the
     * {@link org.axonframework.eventhandling.async.SequentialPerAggregatePolicy}.
     */
    public static final String SEQUENTIAL_PER_AGGREGATE_POLICY = "SequentialPerAggregatePolicy";

    /**
     * A {@link String} constant representing the "meta-data" sequencing policy. The policy utilizes values present in
     * the metadata of an event to define the sequence identifier.
     */
    public static final String META_DATA_SEQUENCING_POLICY = "MetaDataSequencingPolicy";

    /**
     * A {@link String} constant representing the sequential policy. This means all events are handled sequentially. The
     * behavior of this policy resembles the {@link org.axonframework.eventhandling.async.SequentialPolicy}.
     */
    public static final String SEQUENTIAL_POLICY = "SequentialPolicy";

    /**
     * A {@link String} constant representing the full concurrency policy. This means all events are spread out over the
     * available segments, regardless of the sequence identifier. The behavior of this policy resembles the
     * {@link org.axonframework.eventhandling.async.FullConcurrencyPolicy}.
     */
    public static final String FULL_CONCURRENCY_POLICY = "FullConcurrencyPolicy";

    /**
     * A {@link String} constant representing the property sequencing policy. This policy retrieves a value from the
     * event's payload to decide the sequence identifier of the event. The behavior of this policy resembles the
     * {@link org.axonframework.eventhandling.async.PropertySequencingPolicy}.
     */
    public static final String PROPERTY_SEQUENCING_POLICY = "PropertySequencingPolicy";

    private static final String SEQUENTIAL_POLICY_IDENTIFIER = "SequentialPolicy";

    private final String name;
    private final String sequencingPolicy;
    private final List<String> sequencingPolicyParameters;

    /**
     * Instantiates a {@link PersistentStreamSequencingPolicyProvider} based on the given parameters.
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
    public SequencingPolicy<EventMessage<?>> apply(Configuration configuration) {
        return this::sequencingIdentifier;
    }

    private Object sequencingIdentifier(EventMessage<?> event) {
        if (SEQUENTIAL_PER_AGGREGATE_POLICY.equals(sequencingPolicy)) {
            if (event instanceof DomainEventMessage) {
                return ((DomainEventMessage<?>) event).getAggregateIdentifier();
            }
            return event.getIdentifier();
        }

        if (META_DATA_SEQUENCING_POLICY.equals(sequencingPolicy)) {
            List<Object> metaDataValues = new LinkedList<>();
            for (String sequencingPolicyParameter : sequencingPolicyParameters) {
                metaDataValues.add(event.getMetaData().get(sequencingPolicyParameter));
            }
            return metaDataValues;
        }

        if (SEQUENTIAL_POLICY.equals(sequencingPolicy)) {
            return SEQUENTIAL_POLICY_IDENTIFIER;
        }

        if (FULL_CONCURRENCY_POLICY.equals(sequencingPolicy)) {
            return event.getIdentifier();
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
