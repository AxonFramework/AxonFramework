/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.saga.AbstractSagaManager;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link ScopeAwareProvider} which will retrieve a {@link List} of {@link ScopeAware} components
 * in a lazy manner. It does this by pulling these components from the provided {@link Configuration} as soon as
 * #provideScopeAwareStream(ScopeDescriptor) has been called.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public class ConfigurationScopeAwareProvider implements ScopeAwareProvider {

    private List<ScopeAware> scopeAwareComponents;
    private Configuration configuration;

    /**
     * Instantiate a lazy {@link ScopeAwareProvider} with the given {@code configuration} parameter.
     *
     * @param configuration a {@link Configuration} used to retrieve {@link ScopeAware} components from
     */
    public ConfigurationScopeAwareProvider(Configuration configuration) {
        scopeAwareComponents = new ArrayList<>();
        this.configuration = configuration;
    }

    @Override
    public Stream<ScopeAware> provideScopeAwareStream(ScopeDescriptor scopeDescriptor) {
        if (scopeAwareComponents.isEmpty()) {
            lookupScopeAwareComponents();
        }

        return scopeAwareComponents.stream();
    }

    private void lookupScopeAwareComponents() {
        scopeAwareComponents.addAll(retrieveAggregateRepositories());
        scopeAwareComponents.addAll(retrieveSagaManagers());
    }

    private List<Repository> retrieveAggregateRepositories() {
        return configuration.getModules().stream()
                            .filter(module -> module instanceof AggregateConfiguration)
                            .map(module -> (AggregateConfiguration) module)
                            .map((Function<AggregateConfiguration, Repository>) AggregateConfiguration::repository)
                            .collect(Collectors.toList());
    }

    private List<AbstractSagaManager> retrieveSagaManagers() {
        return configuration.getModules().stream()
                            .filter(module -> module instanceof SagaConfiguration)
                            .map(module -> (SagaConfiguration) module)
                            .map((Function<SagaConfiguration, AnnotatedSagaManager>) SagaConfiguration::getSagaManager)
                            .collect(Collectors.toList());
    }
}
