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

import org.axonframework.extension.springboot.UpdateCheckerConfiguration;
import org.axonframework.update.configuration.UsagePropertyProvider;
import org.axonframework.update.detection.TestEnvironmentDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
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
@EnableConfigurationProperties(UpdateCheckerConfiguration.class)
public class UpdateCheckerAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    @Conditional(NotTestEnvironmentCondition.class)
    public UsagePropertyProvider usagePropertyProvider(UpdateCheckerConfiguration updateCheckConfiguration) {
        return UsagePropertyProvider.create(updateCheckConfiguration);
    }

    static class NotTestEnvironmentCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !TestEnvironmentDetector.isTestEnvironment();
        }
    }
}