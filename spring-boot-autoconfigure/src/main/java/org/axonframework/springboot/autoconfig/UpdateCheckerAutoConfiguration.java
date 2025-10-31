package org.axonframework.springboot.autoconfig;

import org.axonframework.springboot.updates.UpdateCheckConfiguration;
import org.axonframework.updates.LoggingUpdateCheckerReporter;
import org.axonframework.updates.UpdateChecker;
import org.axonframework.updates.UpdateCheckerHttpClient;
import org.axonframework.updates.UpdateCheckerReporter;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.axonframework.updates.detection.TestEnvironmentDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@EnableConfigurationProperties(UpdateCheckConfiguration.class)
@AutoConfiguration
public class UpdateCheckerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UsagePropertyProvider usagePropertyProvider(UpdateCheckConfiguration updateCheckConfiguration) {
        return UsagePropertyProvider.create(updateCheckConfiguration);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UpdateCheckerHttpClient updateCheckerHttpClient(UsagePropertyProvider usagePropertyProvider) {
        return new UpdateCheckerHttpClient(usagePropertyProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UpdateCheckerReporter updateCheckerReporter() {
        return new LoggingUpdateCheckerReporter();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UpdateChecker updateChecker(UpdateCheckerHttpClient updateCheckerHttpClient,
                                       UpdateCheckerReporter updateCheckerReporter) {
        return new UpdateChecker(updateCheckerHttpClient, updateCheckerReporter);
    }

    static class NotTestEnvironmentCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !TestEnvironmentDetector.isTestEnvironment();
        }
    }
}
