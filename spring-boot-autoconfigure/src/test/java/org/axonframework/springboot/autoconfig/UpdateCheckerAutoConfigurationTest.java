package org.axonframework.springboot.autoconfig;

import org.axonframework.updates.UpdateChecker;
import org.axonframework.updates.UpdateCheckerHttpClient;
import org.axonframework.updates.UpdateCheckerReporter;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.updates.detection.TestEnvironmentDetector.AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link UpdateCheckerAutoConfiguration}.
 *
 * @author Steven van Beelen
 */
class UpdateCheckerAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        this.testContext = new ApplicationContextRunner()
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues("axon.axonserver.enabled:false");
    }

    @AfterEach
    void cleanup() {
        // Reset the system property after each test to avoid side effects
        System.clearProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT);
    }

    @Test
    void updateCheckerBeansAreRegisteredForNonTestEnvironment() {
        // Force disable the test environment detection
        System.setProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT, "true");

        testContext.run(context -> {
            assertThat(context).hasBean("usagePropertyProvider");
            assertThat(context).hasBean("updateCheckerHttpClient");
            assertThat(context).hasBean("updateCheckerReporter");
            assertThat(context).hasBean("updateChecker");
        });
    }

    @Test
    void updateCheckerBeansAreNotRegisteredForNonTestEnvironment() {
        testContext.run(context -> {
            assertThat(context).doesNotHaveBean("usagePropertyProvider");
            assertThat(context).doesNotHaveBean("updateCheckerHttpClient");
            assertThat(context).doesNotHaveBean("updateCheckerReporter");
            assertThat(context).doesNotHaveBean("updateChecker");
        });
    }

    @Test
    void updateCheckerBeansCanBeOverwritten() {
        testContext.withUserConfiguration(OverrideContext.class).run(context -> {
            assertThat(context).hasBean("customUsagePropertyProvider");
            assertThat(context).hasBean("customUpdateCheckerHttpClient");
            assertThat(context).hasBean("customUpdateCheckerReporter");
            assertThat(context).hasBean("customUpdateChecker");

            assertThat(context).doesNotHaveBean("usagePropertyProvider");
            assertThat(context).doesNotHaveBean("updateCheckerHttpClient");
            assertThat(context).doesNotHaveBean("updateCheckerReporter");
            assertThat(context).doesNotHaveBean("updateChecker");
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class DefaultContext {

    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class OverrideContext {

        @Bean
        public UsagePropertyProvider customUsagePropertyProvider() {
            return mock(UsagePropertyProvider.class);
        }

        @Bean
        public UpdateCheckerHttpClient customUpdateCheckerHttpClient() {
            return mock(UpdateCheckerHttpClient.class);
        }

        @Bean
        public UpdateCheckerReporter customUpdateCheckerReporter() {
            return mock(UpdateCheckerReporter.class);
        }

        @Bean
        public UpdateChecker customUpdateChecker() {
            return mock(UpdateChecker.class);
        }
    }
}