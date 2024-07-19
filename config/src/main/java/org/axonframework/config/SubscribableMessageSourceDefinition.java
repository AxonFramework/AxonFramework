/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.SubscribableMessageSource;

/**
 * Definition for a {@link SubscribableMessageSource}.
 *
 * @param <M> {@link Message} type of the subscribable message source.
 * @author Marc Gathier
 * @since 4.10.0
 */
public interface SubscribableMessageSourceDefinition<M extends Message<?>> {

    /**
     * Creates a {@link SubscribableMessageSource} based on this definition and the provided configuration.
     *
     * @param configuration The Axon {@link Configuration} to base the {@link SubscribableMessageSource} on.
     * @return A {@link SubscribableMessageSource} based on this definition and the provided configuration.
     */
    SubscribableMessageSource<M> create(Configuration configuration);
}
