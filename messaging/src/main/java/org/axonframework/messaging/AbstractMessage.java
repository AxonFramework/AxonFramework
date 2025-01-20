/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;

import java.io.Serial;
import java.util.Map;

/**
 * Abstract base class for {@link Message Messages}.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link AbstractMessage}.
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
public abstract class AbstractMessage<P> implements Message<P> {

    @Serial
    private static final long serialVersionUID = -5847906865361406657L;

    private final String identifier;
    private final MessageType type;

    /**
     * Initializes a new {@link Message} with given {@code identifier} and {@code type}.
     *
     * @param identifier The identifier of this {@link Message}.
     * @param type       The {@link MessageType type} for this {@link Message}.
     */
    public AbstractMessage(@Nonnull String identifier,
                           @Nonnull MessageType type) {
        this.identifier = identifier;
        this.type = type;
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Nonnull
    @Override
    public MessageType type() {
        return this.type;
    }

    @Override
    public Message<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return withMetaData(MetaData.from(metaData));
    }

    @Override
    public Message<P> andMetaData(@Nonnull Map<String, ?> metaData) {
        if (metaData.isEmpty()) {
            return this;
        }
        return withMetaData(getMetaData().mergedWith(metaData));
    }

    /**
     * Returns a new message instance with the same payload and properties as this message but given {@code metaData}.
     *
     * @param metaData The metadata in the new message
     * @return a copy of this instance with given metadata
     */
    protected abstract Message<P> withMetaData(MetaData metaData);
}
