/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.axonframework.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.springframework.kafka.test.rule.KafkaEmbedded;

import java.util.HashMap;
import java.util.Map;

/**
 * Test util for generating {@link ConsumerConfig}.
 *
 * @author Nakul Mishra
 */
public class ConsumerConfigUtil {

    public ConsumerConfigUtil() {
        // private ctor
    }

    public static Map<String, Object> minimal(KafkaEmbedded kafka, String group) {
        return minimal(kafka, group, StringDeserializer.class);
    }

    public static Map<String, Object> minimalTransactional(KafkaEmbedded kafka, String group, Class valueDeserializer) {
        Map<String, Object> configs = minimal(kafka, group, valueDeserializer);
        configs.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return configs;
    }

    public static Map<String, Object> minimal(KafkaEmbedded kafka, String groupName, Class valueDeserializer) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupName);
        return config;
    }

    public static ConsumerFactory<String, String> consumerFactory(KafkaEmbedded kafka, String group) {
        return new DefaultConsumerFactory<>(minimal(kafka, group));
    }

    public static DefaultConsumerFactory<String, Object> transactionalConsumerFactory(KafkaEmbedded kafka, String group,
                                                                                      Class valueDeserializer) {
        return new DefaultConsumerFactory<>(minimalTransactional(kafka, group, valueDeserializer));
    }
}
