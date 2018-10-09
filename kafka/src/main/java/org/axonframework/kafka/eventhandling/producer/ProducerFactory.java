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

package org.axonframework.kafka.eventhandling.producer;

import org.apache.kafka.clients.producer.Producer;

/**
 * The strategy to produce a {@link Producer} instance(s).
 *
 * @param <K> the key type.
 * @param <V> the value type.
 * @author Nakul Mishra
 * @since 3.0
 */
public interface ProducerFactory<K, V> {

    Producer<K, V> createProducer();

    /**
     * What sort of producers to generate. A producer must take confirmation mode into consideration while publishing
     * messages to Kafka.
     *
     * @return configured confirmation mode.
     */
    default ConfirmationMode confirmationMode() {
        return ConfirmationMode.NONE;
    }

    void shutDown();
}
