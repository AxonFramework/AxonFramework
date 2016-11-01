/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.legacy.jdbc;

import org.axonframework.common.Assert;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.legacy.GenericLegacyDomainEventEntry;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * EventStorageEngine implementation that uses JDBC to store and fetch events in a way that is compatible with the event
 * store format of Axon version 2.x.
 * <p>
 * By default the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 */
public class LegacyJdbcEventStorageEngine extends JdbcEventStorageEngine {

    /**
     * Initializes an EventStorageEngine that uses JDBC to store and load events using the default {@link EventSchema}.
     * The payload and metadata of events is stored as a serialized blob of bytes using a new {@link XStreamSerializer}.
     * <p>
     * Events are read in batches of 100. No upcasting is performed after the events have been fetched.
     *
     * @param connectionProvider The provider of connections to the underlying database
     */
    public LegacyJdbcEventStorageEngine(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    /**
     * Initializes an EventStorageEngine that uses JDBC to store and load events using the default {@link EventSchema}.
     * The payload and metadata of events is stored as a serialized blob of bytes using the given {@code serializer}.
     * <p>
     * Events are read in batches of 100. The given {@code upcasterChain} is used to upcast events before
     * deserialization.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}
     *                                     persistence exceptions are not explicitly resolved.
     * @param connectionProvider           The provider of connections to the underlying database
     */
    public LegacyJdbcEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain,
                                        PersistenceExceptionResolver persistenceExceptionResolver, ConnectionProvider
                                                connectionProvider) {
        super(serializer, upcasterChain, persistenceExceptionResolver, connectionProvider);
    }

    /**
     * Initializes an EventStorageEngine that uses JDBC to store and load events.
     *
     * @param serializer                   Used to serialize and deserialize event payload and metadata.
     * @param upcasterChain                Allows older revisions of serialized objects to be deserialized.
     * @param persistenceExceptionResolver Detects concurrency exceptions from the backing database. If {@code null}
     *                                     persistence exceptions are not explicitly resolved.
     * @param batchSize                    The number of events that should be read at each database access. When more
     *                                     than this number of events must be read to rebuild an aggregate's state, the
     *                                     events are read in batches of this size. Tip: if you use a snapshotter, make
     *                                     sure to choose snapshot trigger and batch size such that a single batch will
     *                                     generally retrieve all events required to rebuild an aggregate's state.
     * @param connectionProvider           The provider of connections to the underlying database
     * @param dataType                     The data type for serialized event payload and metadata
     * @param schema                       Object that describes the database schema of event entries
     */
    public LegacyJdbcEventStorageEngine(Serializer serializer, EventUpcaster upcasterChain,
                                        PersistenceExceptionResolver persistenceExceptionResolver, Integer batchSize,
                                        ConnectionProvider connectionProvider, Class<?> dataType, EventSchema schema) {
        super(serializer, upcasterChain, persistenceExceptionResolver, batchSize,
              connectionProvider, dataType, schema, null);
    }

    @Override
    public PreparedStatement readEventData(Connection connection, TrackingToken lastToken) throws SQLException {
        Assert.isTrue(lastToken == null || lastToken instanceof LegacyTrackingToken,
                      () -> String.format("Token [%s] is of the wrong type", lastToken));
        String selectFrom = "SELECT " + trackedEventFields() + " FROM " + schema().domainEventTable();
        String orderBy =
                " ORDER BY " + schema().timestampColumn() + " ASC, " + schema().sequenceNumberColumn() + " ASC, " +
                        schema().aggregateIdentifierColumn() + " ASC";
        if (lastToken == null) {
            return connection.prepareStatement(selectFrom + orderBy);
        } else {
            LegacyTrackingToken lastItem = (LegacyTrackingToken) lastToken;
            String where = " WHERE ((" + schema().timestampColumn() + " > ?) " + "OR (" + schema().timestampColumn() +
                    " = ? AND " + schema().sequenceNumberColumn() + " > ?) " + "OR (" + schema().timestampColumn() +
                    " = ? AND " + schema().sequenceNumberColumn() + " = ? " + "AND " +
                    schema().aggregateIdentifierColumn() + " > ?))";
            PreparedStatement statement = connection.prepareStatement(selectFrom + where + orderBy);
            writeTimestamp(statement, 1, lastItem.getTimestamp());
            writeTimestamp(statement, 2, lastItem.getTimestamp());
            statement.setLong(3, lastItem.getSequenceNumber());
            writeTimestamp(statement, 4, lastItem.getTimestamp());
            statement.setLong(5, lastItem.getSequenceNumber());
            statement.setString(6, lastItem.getAggregateIdentifier());
            return statement;
        }
    }

    @Override
    public TrackedEventData<?> getTrackedEventData(ResultSet resultSet, TrackingToken previousToken) throws SQLException {
        return new GenericLegacyDomainEventEntry<>(resultSet.getString(schema().typeColumn()),
                                                   resultSet.getString(schema().aggregateIdentifierColumn()),
                                                   resultSet.getLong(schema().sequenceNumberColumn()),
                                                   resultSet.getString(schema().eventIdentifierColumn()),
                                                   readTimeStamp(resultSet, schema().timestampColumn()),
                                                   resultSet.getString(schema().payloadTypeColumn()),
                                                   resultSet.getString(schema().payloadRevisionColumn()),
                                                   readPayload(resultSet, schema().payloadColumn()),
                                                   readPayload(resultSet, schema().metaDataColumn()));
    }

    @Override
    protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    protected void writeTimestamp(PreparedStatement preparedStatement, int position,
                                  Instant timestamp) throws SQLException {
        preparedStatement.setString(position, Instant.from(timestamp).toString());
    }

    @Override
    protected String trackedEventFields() {
        return domainEventFields();
    }
}
