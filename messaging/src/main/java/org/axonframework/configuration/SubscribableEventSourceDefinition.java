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

package org.axonframework.configuration;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.SubscribableEventSource;

/**
 * Definition for a {@link SubscribableEventSource}.
 *
 * @param <M> {@link Message} type of the subscribable message source.
 * @author Marc Gathier
 * @since 4.10.0
 */
public interface SubscribableEventSourceDefinition {

    /**
     * Creates a {@link SubscribableEventSource} based on this definition and the provided configuration.
     *
     * @param configuration The Axon {@link Configuration} to base the {@link SubscribableEventSource} on.
     * @return A {@link SubscribableEventSource} based on this definition and the provided configuration.
     */
    SubscribableEventSource create(Configuration configuration);
}
