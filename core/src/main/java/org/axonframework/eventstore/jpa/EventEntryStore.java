/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

import java.util.Iterator;
import java.util.Map;
import javax.persistence.EntityManager;

/**
 * Interface describing the mechanism that stores Events into the backing data store.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface EventEntryStore {

    /**
     * Persists the given <code>event</code> which has been serialized into <code>serializedEvent</code> in the
     * backing data store using given <code>entityManager</code>.
     * <p/>
     * These events should be returned by the <code>fetchAggregateStream(...)</code> methods.
     *
     * @param aggregateType      The type identifier of the aggregate that generated the event
     * @param event              The actual event instance. May be used to extract relevant meta data
     * @param serializedPayload  The serialized payload of the event
     * @param serializedMetaData The serialized MetaData of the event
     * @param entityManager      The entity manager providing access to the data store
     */
    void persistEvent(String aggregateType, DomainEventMessage event, SerializedObject serializedPayload,
                      SerializedObject serializedMetaData, EntityManager entityManager);

    /**
     * Load the last known snapshot event for aggregate of given <code>type</code> with given <code>identifier</code>
     * using given <code>entityManager</code>.
     *
     * @param aggregateType The type identifier of the aggregate that generated the event
     * @param identifier    The identifier of the aggregate to load the snapshot for
     * @param entityManager The entity manager providing access to the data store
     * @return the serialized representation of the last known snapshot event
     */
    SerializedDomainEventData loadLastSnapshotEvent(String aggregateType, Object identifier,
                                                    EntityManager entityManager);

    /**
     * Creates an iterator that iterates through the events for an aggregate of given <code>type</code> and given
     * <code>identifier</code> starting at given <code>firstSequenceNumber</code>. When using batched fetching, the
     * given <code>batchSize</code> should be used. The given <code>entityManager</code> provides access to the backing
     * data store.
     * <p/>
     * Note that the result is expected to be ordered by sequence number, with the lowest number first.
     *
     * @param aggregateType       The type identifier of the aggregate that generated the event
     * @param identifier          The identifier of the aggregate to load the snapshot for
     * @param firstSequenceNumber The sequence number of the first event to include in the batch
     * @param batchSize           The number of entries to include in the batch (if available)
     * @param entityManager       The entity manager providing access to the data store
     * @return a List of serialized representations of Events included in this batch
     */
    Iterator<? extends SerializedDomainEventData> fetchAggregateStream(String aggregateType, Object identifier,
                                                                       long firstSequenceNumber, int batchSize,
                                                                       EntityManager entityManager);

    /**
     * Creates an iterator that iterates through the Events that conform to the given JPA <code>whereClause</code>.
     * When the implementation uses batched fetching, it should use given <code>batchSize</code>. The given
     * <code>parameters</code> provide the values for the placeholders used in the where clause.
     * <p/>
     * The "WHERE" keyword must not be included in the <code>whereClause</code>. If the clause is null or an empty
     * String, no filters are applied, and an iterator is returned that scans <em>all</em> events in the event store.
     * <p/>
     * The iterator should return events in the order they were added to the event store. In either case, it must
     * ensure that events originating from the same aggregate are always returned with the lowest sequence number
     * first.
     *
     * @param whereClause   The JPA clause to be included after the WHERE keyword
     * @param parameters    A map containing all the parameter values for parameter keys included in the where clause
     * @param batchSize     The total number of events to return in this batch
     * @param entityManager The entity manager providing access to the data store
     * @return a List of serialized representations of Events included in this batch
     */
    Iterator<? extends SerializedDomainEventData> fetchFiltered(String whereClause, Map<String, Object> parameters,
                                                                int batchSize, EntityManager entityManager);

    /**
     * Removes old snapshots from the storage for an aggregate of given <code>type</code> that generated the given
     * <code>mostRecentSnapshotEvent</code>. A number of <code>maxSnapshotsArchived</code> is expected to remain in the
     * archive after pruning, unless that number of snapshots has not been created yet. The given
     * <code>entityManager</code> provides access to the data store.
     *
     * @param type                    the type of the aggregate for which to prune snapshots
     * @param mostRecentSnapshotEvent the last appended snapshot event
     * @param maxSnapshotsArchived    the number of snapshots that may remain archived
     * @param entityManager           the entityManager providing access to the data store
     */
    void pruneSnapshots(String type, DomainEventMessage mostRecentSnapshotEvent, int maxSnapshotsArchived,
                        EntityManager entityManager);

    /**
     * Persists the given <code>event</code> which has been serialized into <code>serializedEvent</code> in the
     * backing data store using given <code>entityManager</code>.
     * <p/>
     * These snapshot events should be returned by the <code>loadLastSnapshotEvent(...)</code> methods.
     *
     * @param aggregateType      The type identifier of the aggregate that generated the event
     * @param snapshotEvent      The actual snapshot event instance. May be used to extract relevant meta data
     * @param serializedPayload  The serialized payload of the event
     * @param serializedMetaData The serialized MetaData of the event
     * @param entityManager      The entity manager providing access to the data store
     */
    void persistSnapshot(String aggregateType, DomainEventMessage snapshotEvent, SerializedObject serializedPayload,
                         SerializedObject serializedMetaData, EntityManager entityManager);
}
