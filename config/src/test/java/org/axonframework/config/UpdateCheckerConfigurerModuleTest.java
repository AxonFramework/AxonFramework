package org.axonframework.config;

import org.axonframework.updates.UpdateChecker;
import org.axonframework.updates.UpdateCheckerHttpClient;
import org.axonframework.updates.UpdateCheckerReporter;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.junit.jupiter.api.*;

import static org.axonframework.updates.detection.TestEnvironmentDetector.AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerConfigurerModuleTest {

    @BeforeEach
    void setUp() {
        System.setProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT, "true");
    }

    @AfterEach
    void tearDown() {
        // Reset the system property after tests
        System.clearProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT);
    }

    @Test
    void configureShouldConfigureComponentsWithStartAndShutdown() {
        UpdateCheckerConfigurerModule enhancer = new UpdateCheckerConfigurerModule();
        Configurer testConfigurer = DefaultConfigurer.defaultConfiguration();
        assertDoesNotThrow(() -> enhancer.configureModule(testConfigurer));

        Configuration resultConfig = testConfigurer.buildConfiguration();

        assertNotNull(resultConfig.getComponent(UsagePropertyProvider.class));
        assertNotNull(resultConfig.getComponent(UpdateCheckerHttpClient.class));
        assertNotNull(resultConfig.getComponent(UpdateCheckerReporter.class));
        UpdateChecker checker = resultConfig.getComponent(UpdateChecker.class);
        assertNotNull(checker);

        assertFalse(checker.isStarted());

        resultConfig.start();

        assertTrue(checker.isStarted());

        resultConfig.shutdown();

        assertFalse(checker.isStarted());
    }
}