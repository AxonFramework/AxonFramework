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
import static org.mockito.Mockito.when;

/**
 * Tests {@link ConfigurationScopeAwareProvider}.
 *
 * @author Rob van der Linden Vooren
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationScopeAwareProviderTest {

    @Mock
    private Configuration config;

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
        scopeAwareProvider = new ConfigurationScopeAwareProvider(config);
    }

    @Test
    public void providesScopeAwareAggregatesFromModuleConfiguration() {
        when(config.findModules(AggregateConfiguration.class)).thenCallRealMethod();
        when(config.getModules()).thenReturn(asList(new WrappingModuleConfiguration(aggregateConfiguration)));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        List<ScopeAware> scopeAwares = scopeAwareProvider
                .provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(scopeAwares, equalTo(asList(aggregateRepository)));
    }

    @Test
    public void providesScopeAwareSagasFromModuleConfiguration() {
        when(config.findModules(SagaConfiguration.class)).thenCallRealMethod();
        when(config.getModules()).thenReturn(asList(new WrappingModuleConfiguration(sagaConfiguration)));
        when(sagaConfiguration.getSagaManager()).thenReturn(sagaManager);

        List<ScopeAware> scopeAwares = scopeAwareProvider
                .provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(scopeAwares, equalTo(asList(sagaManager)));
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
