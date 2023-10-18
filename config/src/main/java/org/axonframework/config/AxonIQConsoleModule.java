/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.util.MavenArtifactVersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Module related to AxonIQ console, for now it's just logging information about AxonIQ console when it's not suppressed
 * and the console-framework-client is not available.
 *
 * @author Gerard Klijs
 * @since 4.9.0
 */
public class AxonIQConsoleModule implements ModuleConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String DISABLE_CONSOLE_MESSAGE_SYSTEM_PROPERTY = "disable-axoniq-console-message";
    private static final String CONSOLE_CLIENT_MISSING_MESSAGE =
            "\n################################################################################################\n" +
                    "## You have not configured AxonIQ Console. AxonIQ Console provides out-of-the box monitoring  ##\n"
                    +
                    "## and management capabilities for your Axon Application, starting with it is free.           ##\n"
                    +
                    "## Visit https://console.axoniq.io for more information!                                      ##\n"
                    +
                    "## Suppress this message by setting system property " +
                    DISABLE_CONSOLE_MESSAGE_SYSTEM_PROPERTY + " to true.   ##\n" +
                    "################################################################################################\n";

    @Override
    public void initialize(Configuration config) {
        maybeLogConsoleIsAvailable();
    }

    /**
     * Log a message about AxonIQ Console being available if both the console client is not on the classpath and the
     * system variable to disable the message is not set.
     */
    private void maybeLogConsoleIsAvailable() {
        if (consoleMessageEnabled()) {
            String consoleClientVersion = consoleClientVersion();
            if (Objects.isNull(consoleClientVersion)) {
                logger.info(CONSOLE_CLIENT_MISSING_MESSAGE);
            } else {
                logger.trace("Found version {} for console-framework-client.", consoleClientVersion);
            }
        }
    }

    @Nullable
    private String consoleClientVersion() {
        MavenArtifactVersionResolver resolver =
                new MavenArtifactVersionResolver("io.axoniq.console",
                                                 "console-framework-client",
                                                 DefaultConfigurer.class.getClassLoader());
        String version;
        try {
            version = resolver.get();
        } catch (IOException e) {
            return null;
        }
        return version;
    }

    // Default to true if no property has been set
    private boolean consoleMessageEnabled() {
        String disableConsoleMessageSystemProperty = System.getProperty(
                DISABLE_CONSOLE_MESSAGE_SYSTEM_PROPERTY);
        return !Boolean.TRUE.toString().equalsIgnoreCase(disableConsoleMessageSystemProperty);
    }
}
