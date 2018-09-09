package org.axonframework.config;

import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;

import java.util.List;

import static java.util.Arrays.asList;
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
        when(config.getModules()).thenReturn(asList(aggregateConfiguration));
        when(aggregateConfiguration.repository()).thenReturn(aggregateRepository);

        List<ScopeAware> scopeAwares = scopeAwareProvider
                .provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(scopeAwares, equalTo(asList(aggregateRepository)));
    }

    @Test
    public void providesScopeAwareSagasFromModuleConfiguration() {
        when(config.getModules()).thenReturn(asList(sagaConfiguration));
        when(sagaConfiguration.getSagaManager()).thenReturn(sagaManager);

        List<ScopeAware> scopeAwares = scopeAwareProvider
                .provideScopeAwareStream(anyScopeDescriptor())
                .collect(toList());

        assertThat(scopeAwares, equalTo(asList(sagaManager)));
    }

    private static ScopeDescriptor anyScopeDescriptor() {
        return (ScopeDescriptor) () -> "test-scope";
    }
}
