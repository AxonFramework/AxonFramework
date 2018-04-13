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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;

import java.util.Comparator;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;

/**
 * @author Nakul Mishra.
 */
public class MessageAndMetadata
        implements KafkaMetadataProvider<TrackedEventMessage<?>>, Comparable<MessageAndMetadata> {

    private final TrackedEventMessage<?> eventMessage;
    private final int partition;
    private final long offset;
    private final long timestamp;

    public MessageAndMetadata(TrackedEventMessage<?> eventMessage, int partition, long offset, long timestamp) {
        Assert.notNull(eventMessage, () -> "Event may not be null");
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
        this.eventMessage = eventMessage;
    }

    public static MessageAndMetadata from(EventMessage<?> eventMessage, ConsumerRecord<?, ?> record,
                                          KafkaTrackingToken token) {
        return new MessageAndMetadata(
                asTrackedEventMessage(eventMessage, token), record.partition(), record.offset(), record.timestamp()
        );
    }

    @Override
    public int partition() {
        return partition;
    }

    @Override
    public long offset() {
        return offset;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public TrackedEventMessage<?> value() {
        return eventMessage;
    }

    /**
     * Compares {@link ConsumerRecord} based on timestamp.
     * If two records are published at the same time and belongs to:
     * <ul>
     * <li>a). The same partition; than return the one with smaller offset.</li>
     * <li>b). Different partitions; than return any.</li>
     * </ul>
     */
    @Override
    public int compareTo(MessageAndMetadata other) {
        // @formatter:off
        //TODO : Improve readability Compartor.compareLong
        if (Long.compare(this.timestamp(), other.timestamp()) == 0) { // records on different partitions were published at the same time.
            if (Integer.compare(this.partition(), other.partition()) == 0) { // belong to same partition.
                return Long.compare(this.offset(), other.offset());// return the one with smaller offset.
            }
            return Long.compare(this.partition(), other.partition()); // we don't know which one was published first; best effort not loose the event.
        }
        return Long.compare(this.timestamp(), other.timestamp()); // published on different partitions, return the one with smaller timestamp.
        // @formatter:on
    }

    @Override
    public String toString() {
        return "MessageAndMetadata{" +
                "eventMessage=" + eventMessage +
                ", partition=" + partition +
                ", offset=" + offset +
                ", timestamp=" + timestamp +
                '}';
    }
}