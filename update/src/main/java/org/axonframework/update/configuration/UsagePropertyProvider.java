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

package org.axonframework.update.configuration;

import org.axonframework.common.annotation.Internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides properties related to the Anonymous Usage Collection feature. This interface allows for different
 * implementations to provide properties such as whether the collection is disabled and the URL for the collection
 * endpoint.
 * <p>
 * To use this, please {@link #create(UsagePropertyProvider...)} to obtain an instance that combines multiple property
 * providers, such as command line, property file, and default providers.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public interface UsagePropertyProvider {

    /**
     * Returns whether the Anonymous Usage Collection is disabled.
     *
     * @return {@code true} if the collection is disabled, {@code null} if not specified, or {@code false} if enabled.
     */
    Boolean getDisabled();

    /**
     * Returns the URL for the Anonymous Usage Collection endpoint.
     *
     * @return The URL as a {@link String}, or {@code null} if not specified.
     */
    String getUrl();

    /**
     * Returns the priority of this property provider. Higher values indicate higher priority. Providers with higher
     * priority will be checked first when retrieving properties.
     *
     * @return An {@code int} representing the priority of this provider.
     */
    int priority();

    /**
     * Creates a new instance of {@code UsagePropertyProvider} that combines multiple property providers. The providers
     * are sorted by their priority, with the highest priority provider checked first.
     *
     * @param additionalProviders Additional {@code UsagePropertyProviders} that should be attached to the default
     *                            {@link CommandLineUsagePropertyProvider},
     *                            {@link EnvironmentVariableUsagePropertyProvider},
     *                            {@link PropertyFileUsagePropertyProvider}, and {@link DefaultUsagePropertyProvider}.
     * @return A new {@code UsagePropertyProvider} instance.
     */
    static UsagePropertyProvider create(UsagePropertyProvider... additionalProviders) {
        List<UsagePropertyProvider> providers = new ArrayList<>(Arrays.asList(
                new CommandLineUsagePropertyProvider(),
                new EnvironmentVariableUsagePropertyProvider(),
                new PropertyFileUsagePropertyProvider(),
                DefaultUsagePropertyProvider.INSTANCE
        ));
        providers.addAll(Arrays.asList(additionalProviders));
        return new HierarchicalUsagePropertyProvider(providers);
    }
}
