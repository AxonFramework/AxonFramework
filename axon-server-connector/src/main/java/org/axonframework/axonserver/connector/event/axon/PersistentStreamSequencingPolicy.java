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

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * Implements the {@link SequencingPolicy} provider for a persistent stream.
 * The provider returns a sequencing key for an event used for the dead letter queue.
 */
public class PersistentStreamSequencingPolicy
        implements Function<Configuration, SequencingPolicy<? super EventMessage<?>>> {

    private final String name;
    private final AxonServerConfiguration.PersistentStreamProcessorSettings settings;

    /**
     * Instantiates the {@code PersistentStreamSequencingPolicy}.
     * @param name the processor name
     * @param settings persistent stream settings
     */
    public PersistentStreamSequencingPolicy(
            String name, AxonServerConfiguration.PersistentStreamProcessorSettings settings) {
        this.name = name;
        this.settings = settings;
    }

    @Override
    public SequencingPolicy<EventMessage<?>> apply(Configuration configuration) {
        return this::sequencingIdentifier;
    }

    private Object sequencingIdentifier(EventMessage<?> event) {
        if ("SequentialPerAggregatePolicy".equals(settings.getSequencingPolicy())) {
            if (event instanceof DomainEventMessage) {
                return ((DomainEventMessage<?>) event).getAggregateIdentifier();
            }
            return event.getIdentifier();
        }
        if ("MetaDataSequencingPolicy".equals(settings.getSequencingPolicy())) {
            List<Object> metaDataValues = new LinkedList<>();
            for (String sequencingPolicyParameter : settings.getSequencingPolicyParameters()) {
                metaDataValues.add(event.getMetaData().get(sequencingPolicyParameter));
            }
            return metaDataValues;
        }
        if ("SequentialPolicy".equals(settings.getSequencingPolicy())) {
            return "SequentialPolicy";
        }
        if ("FullConcurrencyPolicy".equals(settings.getSequencingPolicy())) {
            return event.getIdentifier();
        }

        if ("PropertySequencingPolicy".equals(settings.getSequencingPolicy())) {
            throw new RuntimeException(
                    name + ": Cannot use PropertySequencingPolicy in combination with dead-letter queue");
        }

        throw new RuntimeException(
                format("%s :Unknown sequencing policy %s in combination with dead-letter queue", name,
                       settings.getSequencingPolicy()));
    }
}
