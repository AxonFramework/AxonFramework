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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.joda.time.DateTime;

/**
 * Interface describing a factory that creates Entities for the JpaEventStore to persist. The EventEntryFactory allows
 * for customization of the exact column types in which the events are stored.
 * <p/>
 * The entity must have the following properties:
 * <ul>
 * <li>type (String)</li>
 * <li>aggregateIdentifier (String)</li>
 * <li>sequenceNumber (long)</li>
 * <li>eventIdentifier (String)</li>
 * <li>timeStamp (any object supported by {@link org.joda.time.DateTime#DateTime(Object)}</li>
 * <li>payloadType (String)</li>
 * <li>payloadRevision (String)</li>
 * <li>payload (the type defined by the EventEntryFactory implementation)</li>
 * <li>metaData (the type defined by the EventEntryFactory implementation)</li>
 * </ul>
 * <p/>
 * The abstract entity (and <code>@MappedSuperClass</code>) provides all required fields, except for
 * <code>payload</code> and <code>metaData</code>.
 *
 * @param <T> The data type in which serialized data is stored. This must correspond
 * @author Allard Buijze
 * @see org.axonframework.eventstore.jpa.DefaultEventEntryFactory
 * @see org.axonframework.eventstore.jpa.AbstractEventEntryData
 * @since 2.3
 */
public interface EventEntryFactory<T> {

    /**
     * Returns the type used to store serialized payloads. This must correspond to the declared type of the
     * snapshot event entry and domain event entry returned by {@link #createSnapshotEventEntry(String,
     * org.axonframework.domain.DomainEventMessage, org.axonframework.serializer.SerializedObject,
     * org.axonframework.serializer.SerializedObject)} and {@link #createDomainEventEntry(String,
     * org.axonframework.domain.DomainEventMessage, org.axonframework.serializer.SerializedObject,
     * org.axonframework.serializer.SerializedObject)} respectively.
     *
     * @return the type used to store serialized payloads
     */
    Class<T> getDataType();

    /**
     * Creates an entity representing a Domain Event, which contains the data provided in the parameters, which can be
     * stored using the JPA Entity Manager configured on the JpaEventStore using this factory.
     *
     * @param aggregateType      The type identifier of the aggregate that generated the domain event
     * @param event              The DomainEventMessage containing the data to store
     * @param serializedPayload  The serialized payload
     * @param serializedMetaData The serialized meta data
     * @return the instance to store using the EntityManager
     */
    Object createDomainEventEntry(String aggregateType, DomainEventMessage event,
                                  SerializedObject<T> serializedPayload, SerializedObject<T> serializedMetaData);

    /**
     * Creates an entity representing a Snapshot Event, which contains the data provided in the parameters, which can
     * be stored using the JPA Entity Manager configured on the JpaEventStore using this factory.
     *
     * @param aggregateType      The type identifier of the aggregate that generated the domain event
     * @param snapshotEvent      The DomainEventMessage containing the data to store
     * @param serializedPayload  The serialized payload
     * @param serializedMetaData The serialized meta data
     * @return the instance to store using the EntityManager
     */
    Object createSnapshotEventEntry(String aggregateType, DomainEventMessage snapshotEvent,
                                    SerializedObject<T> serializedPayload, SerializedObject<T> serializedMetaData);

    /**
     * Returns the entity name of the Domain Event Entry provided by this factory.
     *
     * @return the entity name of the Domain Event Entry provided by this factory
     */
    String getDomainEventEntryEntityName();

    /**
     * Returns the entity name of the Snapshot Event Entry provided by this factory.
     *
     * @return the entity name of the Snapshot Event Entry provided by this factory
     */
    String getSnapshotEventEntryEntityName();

    /**
     * Returns the representation used for the given <code>dateTime</code> in the event entry.
     * <p/>
     * For example, if the date is stored as an ISO-8601 String, this methods return the dateTime.toString().
     *
     * @param dateTime The date to return the representation for
     * @return The value used to store the given date
     */
    Object resolveDateTimeValue(DateTime dateTime);
}
