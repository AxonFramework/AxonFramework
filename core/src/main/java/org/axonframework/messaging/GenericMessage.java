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

package org.axonframework.messaging;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.Map;

/**
 * @author Rene de Waele
 */
public class GenericMessage<T> extends AbstractMessage<T> {

    private final MetaData metaData;
    private final Class<T> payloadType;
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
        this(IdentifierFactory.getInstance().generateIdentifier(), payload,
             CurrentUnitOfWork.correlationData().mergedWith(MetaData.from(metaData)));
    }

    /**
     * Constructor to reconstruct a Message using existing data.
     *
     * @param identifier The identifier of the Message
     * @param payload    The payload of the message
     * @param metaData   The meta data of the message
     */
    @SuppressWarnings("unchecked")
    public GenericMessage(String identifier, T payload, Map<String, ?> metaData) {
        super(identifier);
        this.metaData = MetaData.from(metaData);
        this.payload = payload;
        this.payloadType = (Class<T>) payload.getClass();
    }

    private GenericMessage(GenericMessage<T> original, MetaData metaData) {
        super(original.getIdentifier());
        this.payload = original.getPayload();
        this.payloadType = original.getPayloadType();
        this.metaData = metaData;
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
    public Class<T> getPayloadType() {
        return payloadType;
    }

    @Override
    protected Message<T> withMetaData(MetaData metaData) {
        return new GenericMessage<>(this, metaData);
    }
}
