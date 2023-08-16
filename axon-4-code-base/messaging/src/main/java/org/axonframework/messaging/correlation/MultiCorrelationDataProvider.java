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

package org.axonframework.messaging.correlation;

import org.axonframework.messaging.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CorrelationDataProvider that combines the data of multiple other correlation providers. When multiple instance
 * provide the same keys, a delegate will override the entries provided by previously resolved delegates.
 *
 * @author Allard Buijze
 * @since 2.3
 */
public class MultiCorrelationDataProvider<T extends Message> implements CorrelationDataProvider {

    private final List<? extends CorrelationDataProvider> delegates;

    /**
     * Initialize the correlation data provider, delegating to given {@code correlationDataProviders}.
     *
     * @param correlationDataProviders the providers to delegate to.
     */
    public MultiCorrelationDataProvider(List<? extends CorrelationDataProvider> correlationDataProviders) {
        delegates = new ArrayList<>(correlationDataProviders);
    }

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        Map<String, Object> correlationData = new HashMap<>();
        for (CorrelationDataProvider delegate : delegates) {
            final Map<String, ?> extraData = delegate.correlationDataFor(message);
            if (extraData != null) {
                correlationData.putAll(extraData);
            }
        }
        return correlationData;
    }
}
