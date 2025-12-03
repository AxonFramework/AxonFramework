/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.extension.springboot.UpdateCheckerProperties;
import org.axonframework.update.UpdateChecker;
import org.axonframework.update.UpdateCheckerHttpClient;
import org.axonframework.update.UpdateCheckerReporter;
import org.axonframework.update.configuration.UsagePropertyProvider;
import org.axonframework.update.detection.TestEnvironmentDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Autoconfiguration class constructing the {@link UsagePropertyProvider} that will end up in the
 * {@link org.axonframework.update.UpdateChecker}.
 * <p>
 * The {@code UsagePropertyProvider} will not be constructed if this configuration spins up in a test environment.
 *
 * @author Steven van Beelen
 * @since 4.12.0
 */
@AutoConfiguration
@EnableConfigurationProperties(UpdateCheckerProperties.class)
public class UpdateCheckerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UsagePropertyProvider usagePropertyProvider(UpdateCheckerProperties properties) {
        return UsagePropertyProvider.create(properties);
    }

    /**
     * Bean creation method for the {@link UpdateCheckerHttpClient}.
     * <p>
     * Although a duplicate of the {@link org.axonframework.update.UpdateCheckerConfigurationEnhancer} its logic to add
     * a {@code UpdateCheckerHttpClient} component, this bean creation method provides a Spring-specific mechanism to
     * expect the exact {@code UsagePropertyProvider} constructed in this file (based on type and name). This ensures we
     * do not have to add any component name in the {@code UpdateCheckerConfigurationEnhancer} to pick the
     * Spring-specific {@code UsagePropertyProvider} constructed by this autoconfiguration class.
     *
     * @param usagePropertyProvider The {@code UsagePropertyProvider} to attach to the {@link UpdateCheckerHttpClient}
     *                              under construction.
     * @return A {@code UpdateCheckerHttpClient} based on the given {@code usagePropertyProvider}.
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UpdateCheckerHttpClient updateCheckerHttpClient(UsagePropertyProvider usagePropertyProvider) {
        return new UpdateCheckerHttpClient(usagePropertyProvider);
    }

    /**
     * Bean creation method for the {@link UpdateChecker}.
     * <p>
     * Although a duplicate of the {@link org.axonframework.update.UpdateCheckerConfigurationEnhancer} its logic to add
     * a {@code UpdateChecker} component, this bean creation method provides a Spring-specific mechanism to expect the
     * exact {@code UpdateCheckerHttpClient} and {@link UsagePropertyProvider} constructed in this file (based on type
     * and name). This ensures we do not have to add any component name in the
     * {@code UpdateCheckerConfigurationEnhancer} to pick the Spring-specific {@code UpdateCheckerHttpClient} amd
     * {@code UsagePropertyProvider} constructed by this autoconfiguration class.
     *
     * @param updateCheckerHttpClient The {@code UpdateCheckerHttpClient} to attach to the {@link UpdateChecker} under
     *                                construction.
     * @param updateCheckerReporter   The {@code UpdateCheckerReporter} to attach to the {@link UpdateChecker} under
     *                                construction.
     * @param usagePropertyProvider   The {@code UsagePropertyProvider} to attach to the {@link UpdateChecker} under
     *                                construction.
     * @return A {@code UpdateCheckerHttpClient} based on the given {@code usagePropertyProvider}.
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UpdateChecker updateChecker(UpdateCheckerHttpClient updateCheckerHttpClient,
                                       UpdateCheckerReporter updateCheckerReporter,
                                       UsagePropertyProvider usagePropertyProvider) {
        return new UpdateChecker(updateCheckerHttpClient, updateCheckerReporter, usagePropertyProvider);
    }

    static class NotTestEnvironmentCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !TestEnvironmentDetector.isTestEnvironment();
        }
    }
}