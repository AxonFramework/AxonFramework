/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.clients.consumer.Consumer;

/**
 * The strategy to produce a {@link org.apache.kafka.clients.consumer.Consumer} instance(s).
 *
 * @author Nakul Mishra
 * @since 3.0
 */
@FunctionalInterface
public interface ConsumerFactory<K, V> {

    /**
     * Create a consumer with the settings supplied in configuration properties.
     *
     * @return the consumer.
     */
    Consumer<K, V> createConsumer();
}
