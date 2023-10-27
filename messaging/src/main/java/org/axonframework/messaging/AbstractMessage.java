/*
 * Copyright (c) 2010-2023. Axon Framework
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

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Abstract base class for Messages.
 *
 * @author Rene de Waele
 */
public abstract class AbstractMessage<T> implements Message<T> {

    private static final long serialVersionUID = -5847906865361406657L;
    private final String identifier;

    /**
     * Initializes a new message with given identifier.
     *
     * @param identifier the message identifier
     */
    public AbstractMessage(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public Message<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return withMetaData(MetaData.from(metaData));
    }

    @Override
    public Message<T> andMetaData(@Nonnull Map<String, ?> metaData) {
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
    protected abstract Message<T> withMetaData(MetaData metaData);
}
