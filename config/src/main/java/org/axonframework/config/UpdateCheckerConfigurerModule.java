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

package org.axonframework.config;

import jakarta.annotation.Nonnull;
import org.axonframework.updates.LoggingUpdateCheckerReporter;
import org.axonframework.updates.UpdateChecker;
import org.axonframework.updates.UpdateCheckerHttpClient;
import org.axonframework.updates.UpdateCheckerReporter;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.axonframework.updates.detection.TestEnvironmentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A {@link ConfigurerModule} that registers the {@link UpdateChecker} component. This component is responsible for
 * reporting anonymous usage data to the AxonIQ servers. It is registered during the external connections phase of the
 * lifecycle.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class UpdateCheckerConfigurerModule implements ConfigurerModule {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCheckerConfigurerModule.class);

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        if (TestEnvironmentDetector.isTestEnvironment()) {
            logger.debug("Skipping AxonIQ UpdateChecker as a testsuite environment was detected.");
            return;
        }
        configurer.registerComponent(UsagePropertyProvider.class, c -> UsagePropertyProvider.create())
                  .registerComponent(UpdateCheckerHttpClient.class,
                                     c -> {
                                         UsagePropertyProvider propertyProvider = c.getComponent(
                                                 UsagePropertyProvider.class);
                                         return new UpdateCheckerHttpClient(propertyProvider);
                                     })
                  .registerComponent(UpdateCheckerReporter.class, c -> new LoggingUpdateCheckerReporter())
                  .registerComponent(UpdateChecker.class,
                                     c -> new UpdateChecker(
                                             c.getComponent(UpdateCheckerHttpClient.class),
                                             c.getComponent(UpdateCheckerReporter.class),
                                             c.getComponent(UsagePropertyProvider.class)
                                     ));
    }

    @Override
    public int order() {
        // Ensure this runs last so users can override components such as the UpdateCheckerReporter
        return Integer.MAX_VALUE;
    }
}
