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

package org.axonframework.kafka.eventhandling.consumer;

/**
 * An interface for messages originating from Kafka capable of providing information about their source.
 * @param <V>.
 * @author Nakul Mishra.
 */
public interface KafkaMetadataProvider<V> {

    /**
     * The partition from which record is received
     */
    int partition();

    /**
     * The position of record in the corresponding Kafka partition.
     */
    long offset();

    /**
     * The timestamp of record
     */
    long timestamp();

    /**
     * The value
     */
    V value();
}
