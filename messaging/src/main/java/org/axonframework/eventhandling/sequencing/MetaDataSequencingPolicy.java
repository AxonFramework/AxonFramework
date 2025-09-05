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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the {@link EventMessage}'s
 * {@link org.axonframework.messaging.MetaData}, based on a given {@code metaDataKey}. In the absence of the given
 * {@code metaDataKey} on the {@link org.axonframework.messaging.MetaData}, the {@link EventMessage#identifier()} is
 * used.
 *
 * @author Lucas Campos
 * @since 4.6.0
 */
public class MetaDataSequencingPolicy implements SequencingPolicy {

    private final String metaDataKey;

    /**
     * Instantiate a {@link MetaDataSequencingPolicy}.
     * <p>
     * Will assert that the {@code metaDataKey} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param metaDataKey The key to be used as a lookup for the property to be used as the Sequence Policy.
     */
    public MetaDataSequencingPolicy(@Nonnull String metaDataKey) {
        assertNonNull(metaDataKey, "MetaDataKey value may not be null");
        this.metaDataKey = metaDataKey;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        return Optional.ofNullable(
                event.metaData()
                     .getOrDefault(metaDataKey, event.identifier())
        );
    }
}
