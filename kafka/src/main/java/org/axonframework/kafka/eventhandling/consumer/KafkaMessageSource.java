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

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.StreamableMessageSource;

/**
 * MessageSource implementation that reads messages from a Kafka topic.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    private final Fetcher fetcher;

    /**
     * Initialize the source using the given {@code fetcher} to retrieve messages from the Kafka topic
     *
     * @param fetcher The fetcher to retrieve messages from Kafka
     */
    public KafkaMessageSource(Fetcher fetcher) {
        Assert.notNull(fetcher, () -> "Kafka message fetcher may not be null");
        this.fetcher = fetcher;
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
        Assert.isTrue(trackingToken == null || trackingToken instanceof KafkaTrackingToken, () -> "Invalid token type");
        return fetcher.start((KafkaTrackingToken) trackingToken);
    }
}
