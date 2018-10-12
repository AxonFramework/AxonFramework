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

import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.saga.AbstractSagaManager;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ConfigurationScopeAwareProvider}.
 *
 * @author Rob van der Linden Vooren
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationScopeAwareProviderTest {

    @Mock
    private Configuration configuration;

    @Mock
    private AggregateConfiguration aggregateConfiguration;

    @Mock
    private Repository aggregateRepository;

    @Mock
    private SagaConfiguration sagaConfiguration;

    @Mock
    private AbstractSagaManager sagaManager;

    @Mock
    private EventProcessingModule eventProcessingConfiguration;

    private ConfigurationScopeAwareProvider scopeAwareProvider;

    @Before
    public void setUp() {
        when(configuration.eventProcessingConfiguration()).thenReturn(eventProcessingConfiguration);
        scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
    }

    @Test
    public void providesScopeAwareAggregatesFromModuleConfiguration() {
        when(configuration.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules())
                .thenReturn(singletonList(new WrappingModuleConfiguration(aggregateConfiguration)));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                                                        .collect(toList());

        assertThat(components, equalTo(singletonList(aggregateRepository)));
    }

    @Test
    public void providesScopeAwareSagasFromModuleConfiguration() {
        when(eventProcessingConfiguration.sagaConfigurations())
                .thenReturn(singletonList(sagaConfiguration));
        when(sagaConfiguration.manager()).thenReturn(sagaManager);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                                                        .collect(toList());

        assertThat(components, equalTo(singletonList(sagaManager)));
    }

    @Test
    public void lazilyInitializes() {
        new ConfigurationScopeAwareProvider(configuration);

        verifyZeroInteractions(configuration);
    }

    @Test
    public void cachesScopeAwareComponentsOnceProvisioned() {
        when(configuration.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules())
                .thenReturn(singletonList(new WrappingModuleConfiguration(aggregateConfiguration)));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        // provision once
        List<ScopeAware> first = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                                                   .collect(toList());
        reset(configuration, aggregateConfiguration);

        // provision twice
        List<ScopeAware> second = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor()).collect(toList());
        verifyZeroInteractions(configuration);
        verifyZeroInteractions(aggregateConfiguration);
        assertThat(second, equalTo(first));
    }

    private static ScopeDescriptor anyScopeDescriptor() {
        return (ScopeDescriptor) () -> "test-scope";
    }

    /**
     * Test variant of a {@link #unwrap() wrapping} configuration.
     */
    private static class WrappingModuleConfiguration implements ModuleConfiguration {

        private final ModuleConfiguration delegate;

        WrappingModuleConfiguration(ModuleConfiguration delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initialize(Configuration config) {
            // No-op, only ipmlemented for test case
        }

        @Override
        public void start() {
            // No-op, only ipmlemented for test case
        }

        @Override
        public void shutdown() {
            // No-op, only ipmlemented for test case
        }

        @Override
        public ModuleConfiguration unwrap() {
            return delegate == null ? this : delegate;
        }

        @Override
        public boolean isType(Class<?> type) {
            return type.isAssignableFrom(unwrap().getClass());
        }
    }
}
