/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import java.io.Serializable;

/**
 * Class copied from Axon 2 to be able to restore Axon 2 Quartz triggers from Axon 3.
 *
 * @param <T> payload type
 * @deprecated this class is available for backward compatibility with instances that were serialized with Axon 2. Use
 * {@link org.axonframework.messaging.GenericMessage} instead.
 */
@Deprecated
public class GenericMessage<T> implements Serializable {

    private static final long serialVersionUID = 4672240170797058482L;

    private String identifier;
    private MetaData metaData;
    private Class payloadType;
    private T payload;

    /**
     * Get the message's identifier.
     *
     * @return the message identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Get the message's metadata.
     *
     * @return the message metadata
     */
    public MetaData getMetaData() {
        return metaData;
    }

    /**
     * Get the type of payload of this message.
     *
     * @return the message payload type
     */
    public Class getPayloadType() {
        return payloadType;
    }

    /**
     * Get the payload of this message.
     *
     * @return the message payload
     */
    public T getPayload() {
        return payload;
    }

    private Object readResolve() {
        return new org.axonframework.messaging.GenericMessage<>(identifier, payload, metaData.getValues());
    }
}
