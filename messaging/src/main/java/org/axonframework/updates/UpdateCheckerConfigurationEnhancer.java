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

package org.axonframework.updates;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.lifecycle.Phase;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.axonframework.updates.detection.TestEnvironmentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.axonframework.configuration.ComponentDefinition.ofType;

/**
 * A {@link ConfigurationEnhancer} that registers the {@link UpdateChecker} component. This component is responsible for
 * reporting anonymous usage data to the AxonIQ servers. It is registered during the external connections phase of the
 * lifecycle.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class UpdateCheckerConfigurationEnhancer implements ConfigurationEnhancer {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCheckerConfigurationEnhancer.class);

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        if (TestEnvironmentDetector.isTestEnvironment()) {
            logger.debug("Skipping AxonIQ UpdateChecker as a testsuite environment was detected.");
            return;
        }
        componentRegistry.registerIfNotPresent(ofType(UsagePropertyProvider.class)
                                                       .withBuilder(c -> UsagePropertyProvider.create()))
                         .registerIfNotPresent(ofType(UpdateCheckerHttpClient.class)
                                                       .withBuilder(c -> {
                                                           UsagePropertyProvider propertyProvider = c.getComponent(
                                                                   UsagePropertyProvider.class);
                                                           return new UpdateCheckerHttpClient(propertyProvider);
                                                       }))
                         .registerIfNotPresent(ofType(UpdateCheckerReporter.class)
                                                       .withBuilder(c -> new LoggingUpdateCheckerReporter())
                         )
                         .registerIfNotPresent(ofType(UpdateChecker.class)
                                                       .withBuilder(c -> new UpdateChecker(
                                                               c.getComponent(UpdateCheckerHttpClient.class),
                                                               c.getComponent(UpdateCheckerReporter.class)
                                                       ))
                                                       .onStart(Phase.EXTERNAL_CONNECTIONS, UpdateChecker::start)
                                                       .onShutdown(Phase.EXTERNAL_CONNECTIONS, UpdateChecker::stop));
    }

    @Override
    public int order() {
        // Ensure this runs last so users can override components such as the UpdateCheckerReporter
        return Integer.MAX_VALUE;
    }
}
