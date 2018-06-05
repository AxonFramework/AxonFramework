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
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.axonframework.common.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link ConsumerFactory} implementation to produce a new {@link Consumer} instance.
 * On each invocation of {@link #createConsumer()} it will create a new instance based on properties supplied via
 * {@code configs}
 *
 * @param <K> the key type.
 * @param <V> the value type.
 * @author Nakul Mishra
 */
public class DefaultConsumerFactory<K, V> implements ConsumerFactory<K, V> {

    private final Map<String, Object> configs;

    public DefaultConsumerFactory(Map<String, Object> configs) {
        Assert.isTrue(configs != null, () -> "Config may not be null.");
        this.configs = new HashMap<>(configs);
    }

    @Override
    public Consumer<K, V> createConsumer() {
        return new KafkaConsumer<>(this.configs);
    }

}
