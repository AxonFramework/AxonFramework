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

package org.axonframework.eventsourcing.eventstore.jdbc.statements;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Class which holds the default {@link PreparedStatement} builder methods for use in the {@link
 * JdbcEventStorageEngine}.
 *
 * @author Lucas Campos
 * @since 4.3
 */
public abstract class JdbcEventStorageEngineStatements {

    private JdbcEventStorageEngineStatements() {

    }

    /**
     * Build the PreparedStatement to be used on {@link JdbcEventStorageEngine#createTokenAt(Instant)}. Defaults to:
     * <p/>
     * {@code "SELECT min([globalIndexColumn]) - 1 FROM [domainEventTable] WHERE [timestampColumn] >= ?" }
     * <p/>
     * <b>NOTE:</b> "?" is the Instant parameter from {@link JdbcEventStorageEngine#createTokenAt(Instant)} and should
     * <b>always</b> be present for the PreparedStatement to work.
     * <p>
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @param dateTime   The dateTime where the token will be created.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement createTokenAt(Connection connection, EventSchema schema, Instant dateTime)
            throws SQLException {
        final String sql =
                "SELECT min(" + schema.globalIndexColumn() + ") - 1 FROM " + schema.domainEventTable() + " WHERE "
                        + schema.timestampColumn() + " >= ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, formatInstant(dateTime));
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#appendEvents(List, Serializer)}. Defaults
     * to:
     * <p/>
     * {@code "INSERT INTO [domainEventTable] ([domainEventFields]) VALUES (?,?,?,?,?,?,?,?,?)" }
     * <p/>
     * <b>NOTE:</b> each "?" is a domain event field from {@link EventSchema#domainEventFields()} and should
     * <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection      The connection to the database.
     * @param schema          The EventSchema to be used.
     * @param dataType        The serialized type of the payload and metadata.
     * @param events          The events to be added.
     * @param serializer      The serializer for the payload and metadata.
     * @param timestampWriter Writer responsible for writing timestamp in the correct format for the given database.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
    public static PreparedStatement appendEvents(Connection connection,
                                                 EventSchema schema,
                                                 Class<?> dataType,
                                                 List<? extends EventMessage<?>> events,
                                                 Serializer serializer,
                                                 TimestampWriter timestampWriter)
            throws SQLException {
        final String sql = "INSERT INTO " + schema.domainEventTable() + " (" + schema.domainEventFields() + ") "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);
        for (EventMessage<?> eventMessage : events) {
            DomainEventMessage<?> event = asDomainEventMessage(eventMessage);
            SerializedObject<?> payload = event.serializePayload(serializer, dataType);
            SerializedObject<?> metaData = event.serializeMetaData(serializer, dataType);
            statement.setString(1, event.getIdentifier());
            statement.setString(2, event.getAggregateIdentifier());
            statement.setLong(3, event.getSequenceNumber());
            statement.setString(4, event.getType());
            timestampWriter.writeTimestamp(statement, 5, event.getTimestamp());
            statement.setString(6, payload.getType().getName());
            statement.setString(7, payload.getType().getRevision());
            statement.setObject(8, payload.getData());
            statement.setObject(9, metaData.getData());
            statement.addBatch();
        }
        return statement;
    }

    /**
     * Converts an {@link EventMessage} to a {@link DomainEventMessage}. If the message already is a {@link
     * DomainEventMessage} it will be returned as is. Otherwise a new {@link GenericDomainEventMessage} is made with
     * {@code null} type, {@code aggregateIdentifier} equal to {@code messageIdentifier} and sequence number of 0L.
     * <p>
     * Doing so allows using the {@link DomainEventEntry} to store both a {@link GenericEventMessage} and a {@link
     * GenericDomainEventMessage}.
     *
     * @param event the input event message
     * @param <T>   the type of payload in the message
     * @return the message converted to a domain event message
     */
    protected static <T> DomainEventMessage<T> asDomainEventMessage(EventMessage<T> event) {
        return event instanceof DomainEventMessage<?>
                ? (DomainEventMessage<T>) event
                : new GenericDomainEventMessage<>(null, event.getIdentifier(), 0L, event, event::getTimestamp);
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#lastSequenceNumberFor(String)}. Defaults
     * to:
     * <p/>
     * {@code "SELECT max([sequenceNumberColumn]) FROM [domainEventTable] WHERE [aggregateIdentifierColumn] = ?" }
     * <p/>
     * <b>NOTE:</b> "?" is the aggregateIdentifier parameter from {@link JdbcEventStorageEngine#lastSequenceNumberFor(String)}
     * and should <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection          The connection to the database.
     * @param schema              The EventSchema to be used
     * @param aggregateIdentifier The identifier of the aggregate.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement lastSequenceNumberFor(Connection connection, EventSchema schema,
                                                          String aggregateIdentifier) throws SQLException {
        final String sql = "SELECT max("
                + schema.sequenceNumberColumn() + ") FROM " + schema.domainEventTable() + " WHERE "
                + schema.aggregateIdentifierColumn() + " = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, aggregateIdentifier);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#createTailToken()}. Defaults to:
     * <p/>
     * {@code "SELECT min([globalIndexColumn]) - 1 FROM [domainEventTable]" }
     * <p/>
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement createTailToken(Connection connection, EventSchema schema) throws SQLException {
        final String sql = "SELECT min(" + schema.globalIndexColumn() + ") - 1 FROM " + schema.domainEventTable();
        return connection.prepareStatement(sql);
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#createHeadToken()}. Defaults to:
     * <p/>
     * {@code "SELECT max([globalIndexColumn]) FROM [domainEventTable]" }
     * <p/>
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement createHeadToken(Connection connection, EventSchema schema) throws SQLException {
        final String sql = "SELECT max(" + schema.globalIndexColumn() + ") FROM " + schema.domainEventTable();
        return connection.prepareStatement(sql);
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#storeSnapshot(DomainEventMessage,
     * Serializer)} . Defaults to:
     * <p/>
     * {@code "INSERT INTO [snapshotTable] ([domainEventFields]) VALUES (?,?,?,?,?,?,?,?,?)" }
     * <p/>
     * <b>NOTE:</b> each "?" is a domain event field from {@link EventSchema#domainEventFields()} and should
     * <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection      The connection to the database.
     * @param schema          The EventSchema to be used.
     * @param dataType        The serialized type of the payload and metadata.
     * @param snapshot        The snapshot to be appended.
     * @param serializer      The serializer for the payload and metadata.
     * @param timestampWriter Writer responsible for writing timestamp in the correct format for the given database.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement appendSnapshot(Connection connection,
                                                   EventSchema schema,
                                                   Class<?> dataType,
                                                   DomainEventMessage<?> snapshot,
                                                   Serializer serializer,
                                                   TimestampWriter timestampWriter)
            throws SQLException {
        final String sql = "INSERT INTO "
                + schema.snapshotTable() + " (" + schema.domainEventFields() + ") VALUES (?,?,?,?,?,?,?,?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);
        SerializedObject<?> payload = snapshot.serializePayload(serializer, dataType);
        SerializedObject<?> metaData = snapshot.serializeMetaData(serializer, dataType);
        statement.setString(1, snapshot.getIdentifier());
        statement.setString(2, snapshot.getAggregateIdentifier());
        statement.setLong(3, snapshot.getSequenceNumber());
        statement.setString(4, snapshot.getType());
        timestampWriter.writeTimestamp(statement, 5, snapshot.getTimestamp());
        statement.setString(6, payload.getType().getName());
        statement.setString(7, payload.getType().getRevision());
        statement.setObject(8, payload.getData());
        statement.setObject(9, metaData.getData());
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#storeSnapshot(DomainEventMessage,
     * Serializer)} . Defaults to:
     * <p/>
     * {@code "DELETE FROM [snapshotTable] WHERE [aggregateIdentifierColumn] = ?1 AND [sequenceNumberColumn] < ?2" }
     * <p/>
     * <b>NOTE:</b> "?1" is the aggregateIdentifier and "?2" is the sequenceNumber parameters taken from the snapshot
     * from {@link JdbcEventStorageEngine#storeSnapshot(DomainEventMessage, Serializer)} and they should <b>always</b>
     * be present for the PreparedStatement to work.
     *
     * @param connection          The connection to the database.
     * @param schema              The EventSchema to be used
     * @param aggregateIdentifier The identifier of the aggregate taken from the snapshot.
     * @param sequenceNumber      The sequence number taken from the snapshot.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement deleteSnapshots(Connection connection, EventSchema schema,
                                                    String aggregateIdentifier, long sequenceNumber)
            throws SQLException {
        final String sql = "DELETE FROM " + schema.snapshotTable() + " WHERE " + schema.aggregateIdentifierColumn()
                + " = ? AND " + schema.sequenceNumberColumn() + " < ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, aggregateIdentifier);
        statement.setLong(2, sequenceNumber);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)}.
     * Defaults to:
     * <p/>
     * {@code "SELECT min([globalIndexColumn]) FROM [domainEventTable] WHERE [globalIndexColumn] > ?" }
     * <p/>
     * <b>NOTE:</b> "?" is based on the lastToken parameter from {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken,
     * int)} and should <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @param index      The index taken from the tracking token.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement fetchTrackedEvents(Connection connection, EventSchema schema, long index)
            throws SQLException {
        final String sql =
                "SELECT min(" + schema.globalIndexColumn() + ") FROM " + schema.domainEventTable() + " WHERE "
                        + schema.globalIndexColumn() + " > ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, index);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on internal cleanGaps operation. Defaults to:
     * <p/>
     * {@code "SELECT [globalIndexColumn], [timestampColumn] FROM [domainEventTable] WHERE [globalIndexColumn] >= ?1 AND
     * [globalIndexColumn] <= ?2" }
     * <p/>
     * <b>NOTE:</b> "?1" and "?2" are taken from the {@link GapAwareTrackingToken#getGaps()} first and last.
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @param gaps       The Set of gaps taken from the tracking token.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement cleanGaps(Connection connection, EventSchema schema, SortedSet<Long> gaps)
            throws SQLException {
        final String sql = "SELECT "
                + schema.globalIndexColumn() + ", " + schema.timestampColumn() + " FROM " + schema
                .domainEventTable() + " WHERE " + schema.globalIndexColumn() + " >= ? AND " + schema
                .globalIndexColumn() + " <= ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, gaps.first());
        statement.setLong(2, gaps.last() + 1L);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#fetchDomainEvents(String, long, int)}
     * <p/>
     * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE [aggregateIdentifierColumn] = ?1 AND
     * [sequenceNumberColumn] >= ?2 AND [sequenceNumberColumn] < ?3 ORDER BY [sequenceNumberColumn] ASC" }
     * <p/>
     * <b>NOTE:</b> "?1" is the identifier, "?2" is the firstSequenceNumber and "?3" is based on batchSize
     * parameters from {@link JdbcEventStorageEngine#fetchDomainEvents(String, long, int)} and they should
     * <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection          The connection to the database.
     * @param schema              The EventSchema to be used
     * @param identifier          The identifier of the aggregate.
     * @param firstSequenceNumber The expected sequence number of the first returned entry.
     * @param batchSize           The number of items to include in the batch.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement readEventDataForAggregate(Connection connection, EventSchema schema,
                                                              String identifier, long firstSequenceNumber,
                                                              int batchSize) throws SQLException {
        final String sql =
                "SELECT " + schema.trackedEventFields() + " FROM " + schema.domainEventTable() + " WHERE " + schema
                        .aggregateIdentifierColumn() + " = ? AND " + schema.sequenceNumberColumn() + " >= ? AND "
                        + schema.sequenceNumberColumn() + " < ? ORDER BY "
                        + schema.sequenceNumberColumn() + " ASC";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, identifier);
        statement.setLong(2, firstSequenceNumber);
        statement.setLong(3, firstSequenceNumber + batchSize);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#readSnapshotData(String)}. Defaults to:
     * <p/>
     * {@code "SELECT [domainEventFields] FROM [snapshotTable] WHERE [aggregateIdentifierColumn] = ? ORDER BY
     * [sequenceNumberColumn] DESC" }
     * <p/>
     * <b>NOTE:</b> "?" is the identifier parameter from {@link JdbcEventStorageEngine#readSnapshotData(String)}
     * and should <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @param identifier The identifier of the aggregate.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement readSnapshotData(Connection connection, EventSchema schema, String identifier)
            throws SQLException {
        final String sql = "SELECT "
                + schema.domainEventFields() + " FROM " + schema.snapshotTable() + " WHERE "
                + schema.aggregateIdentifierColumn() + " = ? ORDER BY " + schema.sequenceNumberColumn()
                + " DESC";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, identifier);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)}
     * when there is no gaps on the {@link GapAwareTrackingToken}. Defaults to:
     * <p/>
     * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE ([globalIndexColumn] > ?1 AND
     * [globalIndexColumn] <= ?2) ORDER BY [globalIndexColumn] ASC" }
     * <p/>
     * <b>NOTE:</b> "?1" is the globalIndex and "?2" is the batchSize parameters from {@link
     * JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)} and they should <b>always</b> be present for the
     * PreparedStatement to work.
     *
     * @param connection  The connection to the database.
     * @param schema      The EventSchema to be used
     * @param globalIndex The index taken from the tracking token.
     * @param batchSize   The number of items to include in the batch
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement readEventDataWithoutGaps(Connection connection, EventSchema schema,
                                                             long globalIndex, int batchSize) throws SQLException {
        final String sql = "SELECT "
                + schema.trackedEventFields() + " FROM " + schema.domainEventTable() + " WHERE ("
                + schema.globalIndexColumn() + " > ? AND " + schema.globalIndexColumn()
                + " <= ?) ORDER BY " + schema.globalIndexColumn() + " ASC";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, globalIndex);
        statement.setLong(2, globalIndex + batchSize);
        return statement;
    }

    /**
     * Set the PreparedStatement to be used on {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)}
     * when there are gaps on the {@link GapAwareTrackingToken}. Defaults to:
     * <p/>
     * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE ([globalIndexColumn] > ?1 AND
     * [globalIndexColumn] <= ?2) OR [globalIndexColumn] IN (?3 .. ?n) ORDER BY [globalIndexColumn] ASC" }
     * <p/>
     * <b>NOTE:</b> "?1" is the globalIndex and "?2" is the batchSize parameters from {@link
     * JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)}. "?3 .. ?n" is taken from the {@link
     * GapAwareTrackingToken#getGaps()} and they should <b>always</b> be present for the PreparedStatement to work.
     *
     * @param connection  The connection to the database.
     * @param schema      The EventSchema to be used
     * @param globalIndex The index taken from the tracking token.
     * @param batchSize   The number of items to include in the batch
     * @param gaps        The Set of gaps taken from the tracking token.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    public static PreparedStatement readEventDataWithGaps(Connection connection, EventSchema schema, long globalIndex,
                                                          int batchSize, List<Long> gaps) throws SQLException {
        final Integer gapSize = gaps.size();
        final String sql =
                "SELECT " + schema.trackedEventFields() + " FROM " + schema.domainEventTable() + " WHERE ("
                        + schema.globalIndexColumn() + " > ? AND " + schema.globalIndexColumn() + " <= ?) OR "
                        + schema.globalIndexColumn() + " IN (" + String.join(",", Collections.nCopies(gapSize, "?"))
                        + ") ORDER BY " + schema.globalIndexColumn() + " ASC";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, globalIndex);
        statement.setLong(2, globalIndex + batchSize);
        for (int i = 0; i < gapSize; i++) {
            statement.setLong(i + 3, gaps.get(i));
        }
        return statement;
    }
}
