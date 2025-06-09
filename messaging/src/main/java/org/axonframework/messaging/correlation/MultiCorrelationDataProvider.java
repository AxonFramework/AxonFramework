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

package org.axonframework.messaging.correlation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code CorrelationDataProvider} that combines the data of multiple other correlation providers. When multiple
 * instance provide the same keys, a delegate will override the entries provided by previously resolved delegates.
 *
 * @author Allard Buijze
 * @since 2.3.0
 */
public class MultiCorrelationDataProvider implements CorrelationDataProvider {

    private final List<? extends CorrelationDataProvider> delegates;

    /**
     * Initialize the correlation data provider, delegating to given {@code correlationDataProviders}.
     *
     * @param correlationDataProviders The providers to delegate to.
     */
    public MultiCorrelationDataProvider(@Nonnull List<? extends CorrelationDataProvider> correlationDataProviders) {
        delegates = new ArrayList<>(correlationDataProviders);
    }

    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message<?> message) {
        Map<String, String> correlationData = new HashMap<>();
        for (CorrelationDataProvider delegate : delegates) {
            correlationData.putAll(delegate.correlationDataFor(message));
        }
        return correlationData;
    }
}
