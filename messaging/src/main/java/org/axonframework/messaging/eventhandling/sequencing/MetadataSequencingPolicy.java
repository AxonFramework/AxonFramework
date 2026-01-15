/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;

import static org.axonframework.common.BuilderUtils.*;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the {@link EventMessage}'s
 * {@link Metadata}, based on a given {@code metadataKey}. In the absence of the given
 * {@code metadataKey} on the {@link Metadata}, the {@link Optional#empty()} is returned.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class MetadataSequencingPolicy implements SequencingPolicy {

    private final String metadataKey;

    /**
     * Instantiate a {@link MetadataSequencingPolicy}.
     * <p>
     * Will assert that the {@code metadataKey} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param metadataKey The key to be used as a lookup for the property to be used as the Sequence Policy.
     */
    public MetadataSequencingPolicy(@Nonnull String metadataKey) {
        this.metadataKey = assertNonBlank(metadataKey, "MetadataKey value may not be null or blank.");
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return Optional.ofNullable(event.metadata().get(metadataKey));
    }
}
