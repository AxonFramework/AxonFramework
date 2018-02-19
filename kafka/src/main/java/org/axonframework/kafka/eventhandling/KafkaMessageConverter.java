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

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.axonframework.eventhandling.EventMessage;

import java.util.Optional;

/**
 * Converts Kafka Message from Axon Message and vice versa.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public interface KafkaMessageConverter<K, V> {

    /**
     * Creates {@link ProducerRecord} for a given {@link EventMessage}
     *
     * @param eventMessage Event message to send to kafka
     * @param topic        The topic the record will be appended to
     * @return the ProducerRecord
     */
    ProducerRecord<K, V> createKafkaMessage(EventMessage<?> eventMessage, String topic);

    /**
     * Reconstruct an EventMessage from the given  {@link ConsumerRecord}. The returned optional
     * resolves to a message if the given input parameters represented a correct event message.
     *
     * @param consumerRecord Event message represented inside kafka
     * @return The Event Message to publish on the local event processors
     */
    Optional<EventMessage<?>> readKafkaMessage(ConsumerRecord<K, V> consumerRecord);
}
