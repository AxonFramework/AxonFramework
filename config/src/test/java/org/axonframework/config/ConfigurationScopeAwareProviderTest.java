/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.saga.AbstractSagaManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.*;

import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests validating the {@link ConfigurationScopeAwareProvider}.
 *
 * @author Rob van der Linden Vooren
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationScopeAwareProviderTest {

    @Mock
    private Configuration configuration;

    @Mock
    private AggregateConfiguration<Object> aggregateConfiguration;

    @Mock
    private Repository<Object> aggregateRepository;

    @Mock
    private SagaConfiguration<Object> sagaConfiguration;

    @Mock
    private AbstractSagaManager<Object> sagaManager;

    @Mock
    private EventProcessingModule eventProcessingConfiguration;

    private ConfigurationScopeAwareProvider scopeAwareProvider;

    @BeforeEach
    void setUp() {
        when(configuration.eventProcessingConfiguration()).thenReturn(eventProcessingConfiguration);
        scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
    }

    @Test
    void providesScopeAwareAggregatesFromModuleConfiguration() {
        when(configuration.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules())
                .thenReturn(singletonList(new WrappingModuleConfiguration(aggregateConfiguration)));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                                                        .collect(toList());

        assertEquals(singletonList(aggregateRepository), components);
    }

    @Test
    void providesScopeAwareSagasFromModuleConfiguration() {
        when(eventProcessingConfiguration.sagaConfigurations())
                .thenReturn(singletonList(sagaConfiguration));
        when(sagaConfiguration.manager()).thenReturn(sagaManager);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                                                        .collect(toList());

        assertEquals(singletonList(sagaManager), components);
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void lazilyInitializes() {
        new ConfigurationScopeAwareProvider(configuration);

        verifyNoInteractions(configuration);
    }

    @Test
    void cachesScopeAwareComponentsOnceProvisioned() {
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
        verifyNoInteractions(configuration);
        verifyNoInteractions(aggregateConfiguration);
        assertEquals(first, second);
    }
    
    @Test
    void canWorkWithoutEventProcessingConfiguration() {
        when(configuration.eventProcessingConfiguration()).thenReturn(null);
        scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
        
        assertDoesNotThrow(() -> scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor()));
    }

    private static ScopeDescriptor anyScopeDescriptor() {
        return () -> "test-scope";
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
            // No-op, only implemented for test case
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
