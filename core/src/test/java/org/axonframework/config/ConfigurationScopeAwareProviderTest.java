package org.axonframework.config;

import org.axonframework.commandhandling.model.AggregateScopeDescriptor;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaScopeDescriptor;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Random;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

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
    private AnnotatedSagaManager sagaManager;

    private ConfigurationScopeAwareProvider scopeAwareProvider;

    @Before
    public void setUp() {
        scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
    }

    @Test
    public void providesScopeAwareAggregatesFromModuleConfiguration() {
        when(configuration.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules()).thenReturn(asList(new WrappingModuleConfiguration(aggregateConfiguration)));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(components, equalTo(asList(aggregateRepository)));
    }

    @Test
    public void providesScopeAwareSagasFromModuleConfiguration() {
        when(configuration.findModules(SagaConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules()).thenReturn(asList(new WrappingModuleConfiguration(sagaConfiguration)));
        when(sagaConfiguration.getSagaManager()).thenReturn(sagaManager);

        List<ScopeAware> components = scopeAwareProvider.provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(components, equalTo(asList(sagaManager)));
    }

    @Test
    public void lazilyInitializes() {
        new ConfigurationScopeAwareProvider(configuration);

        verifyZeroInteractions(configuration);
    }

    @Test
    public void cachesScopeAwareComponentsOnceProvisioned() {
        when(configuration.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(configuration.getModules()).thenReturn(asList(new WrappingModuleConfiguration(aggregateConfiguration)));
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
        String id = randomUUID().toString();
        if (new Random().nextBoolean()) {
            return new AggregateScopeDescriptor("Aggregate", id);
        }
        return new SagaScopeDescriptor("Saga", id);
    }

    /**
     * Test variant of a {@link #unwrap() wrapping} configuration.
     */
    static class WrappingModuleConfiguration implements ModuleConfiguration {

        private final ModuleConfiguration delegate;

        public WrappingModuleConfiguration(ModuleConfiguration delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initialize(Configuration config) {
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public ModuleConfiguration unwrap() {
            return delegate == null ? this : delegate;
        }
    }
}
