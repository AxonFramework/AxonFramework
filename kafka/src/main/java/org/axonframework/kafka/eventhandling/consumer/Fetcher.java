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

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.TrackedEventMessage;

/**
 * Interface describing the component responsible for reading messages from Kafka.
 *
 * @author Nakul Mishra
 * @since 3.3
 */
public interface Fetcher {

    /**
     * Open a stream of messages, starting at the position indicated by the given {@code token}.
     *
     * @param token the token representing positions of the partition to start from
     * @return a stream providing messages from Kafka
     */
    BlockingStream<TrackedEventMessage<?>> start(KafkaTrackingToken token);

    /**
     * Shuts the fetcher down, closing any resources used by this fetcher.
     */
    void shutdown();
}
