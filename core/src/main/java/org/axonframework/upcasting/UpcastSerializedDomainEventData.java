/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.upcasting;

import org.axonframework.eventstore.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

import java.time.Instant;


/**
 * SerializedDomainEventData implementation that can be used to duplicate existing SerializedDomainEventData instances
 * after upcasting a payload.
 *
 * @param <T> The data type representing the serialized object
 * @author Allard Buijze
 * @since 2.0
 */
public class UpcastSerializedDomainEventData<T> implements SerializedDomainEventData<T> {

    private final SerializedDomainEventData<T> original;
    private final String identifier;
    private final SerializedObject<T> upcastPayload;

    /**
     * Reconstruct the given <code>original</code> SerializedDomainEventData replacing the aggregateIdentifier with
     * given <code>aggregateIdentifier</code> and payload with <code>upcastPayload</code>. Typically, for each payload
     * instance returned after an upcast, a single UpcastSerializedDomainEventData instance is to be created.
     *
     * @param original            The original SerializedDomainEventData instance
     * @param aggregateIdentifier The aggregate identifier instance
     * @param upcastPayload       The replacement payload
     */
    public UpcastSerializedDomainEventData(SerializedDomainEventData<T> original, String aggregateIdentifier,
                                           SerializedObject<T> upcastPayload) {
        this.original = original;
        this.identifier = aggregateIdentifier;
        this.upcastPayload = upcastPayload;
    }

    @Override
    public String getEventIdentifier() {
        return original.getEventIdentifier();
    }

    @Override
    public String getAggregateIdentifier() {
        return identifier;
    }

    @Override
    public long getSequenceNumber() {
        return original.getSequenceNumber();
    }

    @Override
    public Instant getTimestamp() {
        return original.getTimestamp();
    }

    @Override
    public SerializedObject<T> getMetaData() {
        return original.getMetaData();
    }

    @Override
    public SerializedObject<T> getPayload() {
        return upcastPayload;
    }

    @Override
    public String getType() {
        return original.getType();
    }
}
