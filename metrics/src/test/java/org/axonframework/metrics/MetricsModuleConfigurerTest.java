package org.axonframework.metrics;

import org.axonframework.config.Configurer;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MetricsModuleConfigurerTest {

    private GlobalMetricRegistry globalMetricRegistry;
    private MetricsModuleConfigurer metricsModuleConfigurer;

    @Before
    public void setUp() {
        globalMetricRegistry = mock(GlobalMetricRegistry.class);
        metricsModuleConfigurer = new MetricsModuleConfigurer(globalMetricRegistry);
    }

    @Test
    public void testConfigureModuleCallsGlobalMetricRegistry() {
        Configurer configurerMock = mock(Configurer.class);

        metricsModuleConfigurer.configureModule(configurerMock);

        verify(globalMetricRegistry).registerWithConfigurer(configurerMock);
    }
}
