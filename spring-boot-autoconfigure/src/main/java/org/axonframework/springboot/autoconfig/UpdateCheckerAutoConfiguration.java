package org.axonframework.springboot.autoconfig;

import org.axonframework.updates.LoggingUpdateCheckerReporter;
import org.axonframework.updates.UpdateChecker;
import org.axonframework.updates.UpdateCheckerHttpClient;
import org.axonframework.updates.UpdateCheckerReporter;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.axonframework.updates.detection.TestEnvironmentDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@AutoConfiguration
public class UpdateCheckerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(TestEnvironmentCondition.class)
    public UsagePropertyProvider usagePropertyProvider() {
        return UsagePropertyProvider.create();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(TestEnvironmentCondition.class)
    public UpdateCheckerHttpClient updateCheckerHttpClient(UsagePropertyProvider usagePropertyProvider) {
        return new UpdateCheckerHttpClient(usagePropertyProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(TestEnvironmentCondition.class)
    public UpdateCheckerReporter updateCheckerReporter() {
        return new LoggingUpdateCheckerReporter();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(TestEnvironmentCondition.class)
    public UpdateChecker updateChecker(UpdateCheckerHttpClient updateCheckerHttpClient,
                                       UpdateCheckerReporter updateCheckerReporter) {
        return new UpdateChecker(updateCheckerHttpClient, updateCheckerReporter);
    }

    static class TestEnvironmentCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !TestEnvironmentDetector.isTestEnvironment();
        }
    }
}
