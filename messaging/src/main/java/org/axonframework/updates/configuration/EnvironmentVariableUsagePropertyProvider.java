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

package org.axonframework.updates.configuration;

import org.axonframework.common.BuilderUtils;

/**
 * A {@link UsagePropertyProvider} implementation that reads the usage properties from the environment variables. The
 * priority is half of max integer value, meaning it will be overridden by the command-line properties provider.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class EnvironmentVariableUsagePropertyProvider implements UsagePropertyProvider {

    /**
     * The environment variable key to check if the update checker is disabled.
     */
    public static final String DISABLED_KEY = "AXONIQ_UPDATE_CHECKER_DISABLED";
    /**
     * The environment variable key to retrieve the URL for the usage collection endpoint.
     */
    public static final String URL_KEY = "AXONIQ_UPDATE_CHECKER_URL";

    private final EnvironmentVariableSupplier envSupplier;

    /**
     * Creates a new {@code EnvironmentVariableUsagePropertyProvider} that reads properties from the system environment.
     * This constructor uses {@link System#getenv()} as the default supplier for environment variables.
     */
    public EnvironmentVariableUsagePropertyProvider() {
        this(System::getenv);
    }

    /**
     * Creates a new {@code EnvironmentVariableUsagePropertyProvider} that reads properties from the provided
     * {@link EnvironmentVariableSupplier}. This allows for custom implementations to provide environment variable
     * values, which can be useful for testing or when the default {@link System#getenv()} is not suitable.
     *
     * @param envSupplier The supplier to use for retrieving environment variables.
     */
    public EnvironmentVariableUsagePropertyProvider(EnvironmentVariableSupplier envSupplier) {
        BuilderUtils.assertNonNull(envSupplier, "The envSupplier must not be null.");
        this.envSupplier = envSupplier;
    }

    @Override
    public Boolean getDisabled() {
        String property = envSupplier.get(DISABLED_KEY);
        if (property != null) {
            return Boolean.parseBoolean(property);
        }
        return null;
    }

    @Override
    public String getUrl() {
        return envSupplier.get(URL_KEY);
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE / 2;
    }

    /**
     * A functional interface to supply environment variables. This allows for custom implementations to provide
     * environment variable values, which can be useful for testing or when the default {@link System#getenv()} is not
     * suitable.
     */
    @FunctionalInterface
    public interface EnvironmentVariableSupplier {

        /**
         * Retrieves the value of the specified environment variable.
         *
         * @param key The name of the environment variable to retrieve.
         * @return The value of the environment variable, or {@code null} if it is not set.
         */
        String get(String key);
    }
}
