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

package org.axonframework.config;

import org.axonframework.common.Assert;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.modelling.command.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of the {@link ScopeAwareProvider} which will retrieve a {@link List} of {@link ScopeAware} components
 * in a lazy manner. It does this by pulling these components from the provided {@link Configuration} as soon as
 * {@link #provideScopeAwareStream(ScopeDescriptor)} is called.
 *
 * @author Steven van Beelen
 * @author Rob van der Linden Vooren
 * @since 3.3
 */
public class ConfigurationScopeAwareProvider implements ScopeAwareProvider {

    private final Configuration configuration;

    private List<ScopeAware> scopeAwareComponents;

    /**
     * Instantiate a lazy {@link ScopeAwareProvider} with the given {@code configuration} parameter.
     *
     * @param configuration a {@link Configuration} used to retrieve {@link ScopeAware} components from
     * @throws IllegalArgumentException when {@code configuration} is {@code null}
     */
    public ConfigurationScopeAwareProvider(Configuration configuration) {
        this.configuration = Assert.nonNull(configuration, () -> "configuration may not be null");
    }

    @Override
    public Stream<ScopeAware> provideScopeAwareStream(ScopeDescriptor scopeDescriptor) {
        if (scopeAwareComponents == null) {
            scopeAwareComponents = retrieveScopeAwareComponents();
        }
        return scopeAwareComponents.stream();
    }

    private List<ScopeAware> retrieveScopeAwareComponents() {
        List<ScopeAware> components = new ArrayList<>();
        components.addAll(retrieveAggregateRepositories());
        return components;
    }

    private List<Repository> retrieveAggregateRepositories() {
        // TODO #3486 - I don't think we require the scope logic at all anymore. If we do, we need to revamp this part.
        return List.of();
//        return configuration.findModules(AggregateConfiguration.class).stream()
//                            .map(AggregateConfiguration::repository)
//                            .collect(toList());
    }
}
