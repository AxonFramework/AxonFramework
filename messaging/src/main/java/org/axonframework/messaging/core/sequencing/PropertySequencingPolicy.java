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

package org.axonframework.messaging.core.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the message payload based on a
 * given property.
 *
 * @param <T> the type of the supported payload
 * @param <K> the type of the extracted property
 * @author Nils Christian Ehmke
 * @since 4.5.2
 */
public class PropertySequencingPolicy<T, K> extends ExtractionSequencingPolicy<T, K> {

    /**
     * Creates a new instance of the {@code PropertySequencingPolicy}, which extracts the sequence identifier from the
     * message payload of the given {@code payloadClass} using the given {@code identifierExtractor}.
     *
     * @param payloadClass The class of the supported payload.
     * @param propertyName The name of the property to be extracted as sequence identifier.
     */
    public PropertySequencingPolicy(
            @Nonnull Class<T> payloadClass,
            @Nonnull String propertyName
    ) {
        super(payloadClass, extractProperty(payloadClass, propertyName)::getValue);
    }

    private static <T> Property<T> extractProperty(@Nonnull Class<T> payloadClass, @Nonnull String propertyName) {
        final Property<T> property = PropertyAccessStrategy.getProperty(payloadClass, propertyName);
        assertNonNull(property, "Property cannot be found");
        return property;
    }
}
