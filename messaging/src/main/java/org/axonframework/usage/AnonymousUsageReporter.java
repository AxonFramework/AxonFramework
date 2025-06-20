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

package org.axonframework.usage;

import org.axonframework.usage.detection.TestEnvironmentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Anonymous usage reporter that starts the reporting task to send anonymous usage data to AxonIQ. The task does not
 * only report usage data, but also checks for library version upgrades and vulnerabilities. This class is intended to
 * be used in production environments, and it skips reporting in test environments.
 * <p>
 * There are two ways to disable the anonymous usage reporting:
 * <ol>
 *     <li>Set the environment variable {@code AXONIQ_USAGE_DISABLED=true}.</li>
 *     <li>Run the JVM with {@code -Daxoniq.usage.disabled=true}.</li>
 *     <li>Create the file {@code $HOME/.axoniq/data-collection.properties} with content {@code disabled=true}</li>
 * </ol>
 * These methods are listed in order of precedence, meaning that if the environment variable is set, it will take precedence over the JVM property and the file.
 * Explicitly setting the property to {@code disabled=false} in a method of higher precedence will ignore the lower precedence disabled.
 *
 * @author Mitchell Herrijgers
 */
public class AnonymousUsageReporter {

    private final Logger logger = LoggerFactory.getLogger(AnonymousUsageReporter.class);
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Starts the anonymous usage reporting task. This method checks if the application is running in a test environment
     * and skips reporting if it is. In production environments, it initializes the reporting task to send anonymous
     * usage data to AxonIQ.
     */
    public void start() {
        if (TestEnvironmentDetector.isTestEnvironment()) {
            logger.debug("Skipping Axon Framework Anonymous Usage Reporting in test environment");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            logger.debug("Axon Framework Anonymous Usage Reporting already started");
            return;
        }
        logger.info(
                "Axon Framework Anonymous Usage Reporting started. This will report anonymous usage data to AxonIQ, "
                        + "and receive information about available upgrades and vulnerabilities in the Axon libraries used. ");
        AnonymousUsageTask.start();
    }
}
