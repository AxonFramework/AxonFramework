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

import org.axonframework.messaging.MetaData;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.time.Instant;

/**
 * Class copied from Axon 2 to be able to restore Axon 2 Quartz triggers from Axon 3.
 * @param <T> payload type
 * @deprecated this class is available for backward compatibility with instances that were serialized with Axon 2. Use
 * {@link org.axonframework.eventhandling.GenericEventMessage} instead.
 */
@Deprecated
public class GenericEventMessage<T> extends GenericMessage<T> implements Serializable {

    private static final long serialVersionUID = -8370948891267874107L;

    private DateTime timestamp;

    private Object readResolve() {
        return new org.axonframework.eventhandling.GenericEventMessage<>(getIdentifier(), getPayload(), MetaData.from(getMetaData().getValues()), Instant.ofEpochMilli(timestamp.getMillis()));
    }
}
