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

import org.apache.kafka.common.header.Headers;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serialization.SerializedObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.kafka.eventhandling.HeaderUtils.generateMetadataKey;
import static org.axonframework.kafka.eventhandling.HeaderUtils.valueAsLong;
import static org.axonframework.kafka.eventhandling.HeaderUtils.valueAsString;
import static org.axonframework.messaging.Headers.AGGREGATE_ID;
import static org.axonframework.messaging.Headers.AGGREGATE_SEQ;
import static org.axonframework.messaging.Headers.AGGREGATE_TYPE;
import static org.axonframework.messaging.Headers.MESSAGE_ID;
import static org.axonframework.messaging.Headers.MESSAGE_REVISION;
import static org.axonframework.messaging.Headers.MESSAGE_TIMESTAMP;
import static org.axonframework.messaging.Headers.MESSAGE_TYPE;

/**
 * Util for asserting Kafka headers sent via Axon.
 *
 * @author Nakul Mishra
 */
class HeaderAssertUtils {

    private HeaderAssertUtils() {
        // private ctor
    }

    static void assertEventHeaders(String metaKey, EventMessage<?> evt, SerializedObject<byte[]> so,
                                   Headers headers) {
        assertThat(headers.toArray().length).isGreaterThanOrEqualTo(5);
        assertThat(valueAsString(headers, MESSAGE_ID)).isEqualTo(evt.getIdentifier());
        assertThat(valueAsLong(headers, MESSAGE_TIMESTAMP)).isEqualTo(evt.getTimestamp().toEpochMilli());
        assertThat(valueAsString(headers, MESSAGE_TYPE)).isEqualTo(so.getType().getName());
        assertThat(valueAsString(headers, MESSAGE_REVISION)).isEqualTo(so.getType().getRevision());
        assertThat(valueAsString(headers, generateMetadataKey(metaKey))).isEqualTo(evt.getMetaData().get(metaKey));
    }

    static void assertDomainHeaders(DomainEventMessage<?> evt, Headers headers) {
        assertThat(headers.toArray().length).isGreaterThanOrEqualTo(8);
        assertThat(valueAsLong(headers, AGGREGATE_SEQ)).isEqualTo(evt.getSequenceNumber());
        assertThat(valueAsString(headers, AGGREGATE_ID)).isEqualTo(evt.getAggregateIdentifier());
        assertThat(valueAsString(headers, AGGREGATE_TYPE)).isEqualTo(evt.getType());
    }
}
