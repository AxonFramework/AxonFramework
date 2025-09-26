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

package org.axonframework.messaging.correlation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link CorrelationDataProviderRegistry}, maintaining a list of
 * {@link CorrelationDataProvider CorrelationDataProviders}.
 * <p>
 * This implementation ensures given correlation data providers factory methods are only invoked once.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class DefaultCorrelationDataProviderRegistry implements CorrelationDataProviderRegistry {

    private final List<ComponentDefinition<CorrelationDataProvider>> providerDefinitions = new ArrayList<>();

    @Nonnull
    @Override
    public CorrelationDataProviderRegistry registerProvider(
            @Nonnull ComponentBuilder<CorrelationDataProvider> providerBuilder
    ) {
        providerDefinitions.add(ComponentDefinition.ofType(CorrelationDataProvider.class)
                                                   .withBuilder(providerBuilder));
        return this;
    }

    @Nonnull
    @Override
    public List<CorrelationDataProvider> correlationDataProviders(@Nonnull Configuration config) {
        List<CorrelationDataProvider> correlationDataProviders = new ArrayList<>();
        for (ComponentDefinition<CorrelationDataProvider> providerDefinition : providerDefinitions) {
            if (!(providerDefinition instanceof ComponentDefinition.ComponentCreator<CorrelationDataProvider> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + providerDefinition);
            }
            CorrelationDataProvider correlationDataProvider = creator.createComponent().resolve(config);
            correlationDataProviders.add(correlationDataProvider);
        }
        return correlationDataProviders;
    }
}
