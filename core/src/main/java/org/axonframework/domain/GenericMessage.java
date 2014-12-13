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

package org.axonframework.domain;

import java.util.Map;

/**
 * Generic implementation of the Message interface.
 *
 * @param <T> The type of payload contained in this message
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericMessage<T> implements Message<T> {

    private static final long serialVersionUID = 4672240170797058482L;

    private final String identifier;
    private final MetaData metaData;
    // payloadType is stored separately, because of Object.getClass() performance
    private final Class payloadType;
    private final T payload;

    /**
     * Constructs a Message for the given <code>payload</code> using empty meta data.
     *
     * @param payload The payload for the message
     */
    public GenericMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a Message for the given <code>payload</code> and <code>meta data</code>.
     *
     * @param payload  The payload for the message
     * @param metaData The meta data for the message
     */
    public GenericMessage(T payload, Map<String, ?> metaData) {
        this(IdentifierFactory.getInstance().generateIdentifier(), payload, MetaData.from(metaData));
    }

    /**
     * Constructor to reconstruct a Message using existing data.
     *
     * @param identifier The identifier of the Message
     * @param payload    The payload of the message
     * @param metaData   The meta data of the message
     */
    public GenericMessage(String identifier, T payload, Map<String, ?> metaData) {
        this.identifier = identifier;
        this.metaData = MetaData.from(metaData);
        this.payload = payload;
        this.payloadType = payload.getClass();
    }

    private GenericMessage(GenericMessage<T> original, Map<String, ?> metaData) {
        this.identifier = original.getIdentifier();
        this.payload = original.getPayload();
        this.payloadType = payload.getClass();
        this.metaData = MetaData.from(metaData);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Class getPayloadType() {
        return payloadType;
    }

    @Override
    public GenericMessage<T> withMetaData(Map<String, ?> newMetaData) {
        if (this.metaData.equals(newMetaData)) {
            return this;
        }
        return new GenericMessage<>(this, newMetaData);
    }

    @Override
    public GenericMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        if (additionalMetaData.isEmpty()) {
            return this;
        }
        return new GenericMessage<>(this, this.metaData.mergedWith(additionalMetaData));
    }
}
