/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;

import java.util.List;
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
     * These events should be returned by the <code>fetchBatch(...)</code> methods.
     *
     * @param aggregateType   The type identifier of the aggregate that generated the event
     * @param event           The actual event instance. May be used to extract relevant meta data
     * @param serializedEvent The serialized version of the event
     * @param entityManager   The entity manager providing access to the data store
     */
    void persistEvent(String aggregateType, DomainEvent event,
                      byte[] serializedEvent, EntityManager entityManager);

    /**
     * Load the last known snapshot event for aggregate of given <code>type</code> with given <code>identifier</code>
     * using given <code>entityManager</code>.
     *
     * @param aggregateType The type identifier of the aggregate that generated the event
     * @param identifier    The identifier of the aggregate to load the snapshot for
     * @param entityManager The entity manager providing access to the data store
     * @return the serialized representation of the last known snapshot event
     */
    byte[] loadLastSnapshotEvent(String aggregateType, AggregateIdentifier identifier, EntityManager entityManager);

    /**
     * Fetches a selection (starting at <code>startPosition</code> and including <code>batchSize</code> entries) of
     * events in the entire event store.
     * <p/>
     * Note that the implementation is responsible for guaranteeing a consistent ordering in the query. Otherwise,
     * batches may not represent a consecutive list of the entire collection of events.
     *
     * @param startPosition The first item to include in the batch
     * @param batchSize     The number of items to include in the batch
     * @param entityManager The entity manager providing access to the data store
     * @return a List of serialized representations of Events included in this batch
     */
    List<byte[]> fetchBatch(int startPosition, int batchSize, EntityManager entityManager);

    /**
     * Fetches a selection of events for an aggregate of given <code>type</code> and given <code>identifier</code>
     * starting at given <code>firstSequenceNumber</code> with given <code>batchSize</code>. The given
     * <code>entityManager</code> provides access to the backing data store.
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
    List<byte[]> fetchBatch(String aggregateType, AggregateIdentifier identifier, long firstSequenceNumber,
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
    void pruneSnapshots(String type, DomainEvent mostRecentSnapshotEvent, int maxSnapshotsArchived,
                        EntityManager entityManager);

    /**
     * Persists the given <code>event</code> which has been serialized into <code>serializedEvent</code> in the
     * backing data store using given <code>entityManager</code>.
     * <p/>
     * These snapshot events should be returned by the <code>loadLastSnapshotEvent(...)</code> methods.
     *
     * @param aggregateType   The type identifier of the aggregate that generated the event
     * @param snapshotEvent   The actual snapshot event instance. May be used to extract relevant meta data
     * @param serializedEvent The serialized version of the event
     * @param entityManager   The entity manager providing access to the data store
     */
    void persistSnapshot(String aggregateType, DomainEvent snapshotEvent, byte[] serializedEvent,
                         EntityManager entityManager);
}
