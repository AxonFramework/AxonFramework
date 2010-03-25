/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.DomainEvent;

/**
 * Interface describing classes that can serialize and deserialize DomainEvents to bytes.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface EventSerializer {

    /**
     * Serialize the given <code>events</code> into a byte[]. The actual encoding used depends on the implementation.
     *
     * @param event The event to serialize
     * @return the byte array representing the serialized domain event.
     */
    byte[] serialize(DomainEvent event);

    /**
     * Deserialize a DomainEvent using the given <code>serializedEvents</code>. Implementations are *not* allowed to
     * change the given <code>serializedEvent</code> (byte array).
     *
     * @param serializedEvent The byte array containing the serialized domain event.
     * @return The DomainEvent instance represented by the provided byte array
     */
    DomainEvent deserialize(byte[] serializedEvent);
}
