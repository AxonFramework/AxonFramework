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

package org.axonframework.usage.configuration;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Combines multiple {@link UsagePropertyProvider} instances into a single provider.
 * It will return the first non-null value for each property from the list of providers, sorted by their priority.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class HierachicalUsagePropertyProvider implements UsagePropertyProvider {

    private final List<UsagePropertyProvider> providers;

    /**
     * Creates a new {@code HierachicalUsagePropertyProvider} with the given list of providers.
     * The providers will be sorted by their priority in descending order, meaning the highest priority provider
     * will be checked first.
     *
     * @param providers The list of {@link UsagePropertyProvider} instances to combine.
     */
    public HierachicalUsagePropertyProvider(List<UsagePropertyProvider> providers) {
        this.providers = providers.stream()
                                  .sorted(Comparator.comparingInt(UsagePropertyProvider::priority).reversed())
                                  .toList();
    }

    @Override
    public Boolean getDisabled() {
        return providers.stream()
                        .map(UsagePropertyProvider::getDisabled)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(false);
    }

    @Override
    public String getUrl() {
        return providers.stream().map(UsagePropertyProvider::getUrl)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("");
    }

    @Override
    public int priority() {
        // Does not matter for the combined provider, as it is not used directly.
        return 0;
    }
}
