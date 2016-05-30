/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

/**
 * @author Rene de Waele
 */
public class GenericTrackedDomainEventEntry<T> extends AbstractTrackedDomainEventEntry<T> {

    public GenericTrackedDomainEventEntry(long globalIndex, String type, String aggregateIdentifier,
                                          long sequenceNumber, String eventIdentifier, Object timeStamp,
                                          String payloadType, String payloadRevision, T payload, T metaData) {
        super(globalIndex, eventIdentifier, timeStamp, payloadType, payloadRevision, payload, metaData, type,
              aggregateIdentifier, sequenceNumber);
    }
}
