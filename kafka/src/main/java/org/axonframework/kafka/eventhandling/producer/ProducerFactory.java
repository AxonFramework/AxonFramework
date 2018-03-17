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
 * @author Nakul Mishra
 * @since 3.0
 */
public interface ProducerFactory<K, V> {

    /**
     * Create a producer with the settings supplied in configuration properties.
     *
     * @return the producer.
     */
    Producer<K, V> createProducer();

    /**
     * Which approach producer should follow while publishing messages to kafka
     *
     * @return configured confirmation mode
     */
    default ConfirmationMode getConfirmationMode() {
        return ConfirmationMode.NONE;
    }
}
