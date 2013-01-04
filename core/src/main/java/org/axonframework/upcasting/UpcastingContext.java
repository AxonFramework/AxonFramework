/*
 * Copyright (c) 2010-2013. Axon Framework
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

import org.axonframework.domain.MetaData;
import org.joda.time.DateTime;

/**
 * Interface describing an object that provides contextual information about the object being upcast. Generally, this
 * is information about the Message containing the object to upcast.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcastingContext {

    /**
     * Returns the identifier of the message wrapping the object to upcast.
     *
     * @return the identifier of the message wrapping the object to upcast, if available
     */
    String getMessageIdentifier();

    /**
     * Returns the Identifier of the Aggregate to which the Event owning the object to upcast, was applied. This will
     * return <code>null</code> if the object being upcast was not contained in a DomainEventMessage.
     *
     * @return the Identifier of the Aggregate to which the Event was applied, or <code>null</code> if not applicable
     */
    Object getAggregateIdentifier();

    /**
     * Returns the sequence number of the event in the aggregate, or <code>null</code> if the message wrapping the
     * object being upcast does not contain a sequence number.
     *
     * @return the sequence number of the event in the aggregate, if available
     */
    Long getSequenceNumber();

    /**
     * Returns the timestamp at which the event was first created. Will return <code>null</code> if the object being
     * upcast
     *
     * @return the timestamp at which the event was first created, if available
     */
    DateTime getTimestamp();

    /**
     * Returns the meta data of the message wrapping the object being upcast. If the meta data is not available, or is
     * in fact the subject of being upcast itself, this method returns <code>null</code>.
     *
     * @return the MetaData of the message wrapping the object to upcast, if available
     */
    MetaData getMetaData();
}
