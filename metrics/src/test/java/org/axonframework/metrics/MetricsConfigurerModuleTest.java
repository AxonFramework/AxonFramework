package org.axonframework.metrics;

import org.axonframework.config.Configurer;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MetricsConfigurerModuleTest {

    private GlobalMetricRegistry globalMetricRegistry;
    private MetricsConfigurerModule metricsConfigurerModule;

    @Before
    public void setUp() {
        globalMetricRegistry = mock(GlobalMetricRegistry.class);
        metricsConfigurerModule = new MetricsConfigurerModule(globalMetricRegistry);
    }

    @Test
    public void testConfigureModuleCallsGlobalMetricRegistry() {
        Configurer configurerMock = mock(Configurer.class);

        metricsConfigurerModule.configureModule(configurerMock);

        verify(globalMetricRegistry).registerWithConfigurer(configurerMock);
    }
}
