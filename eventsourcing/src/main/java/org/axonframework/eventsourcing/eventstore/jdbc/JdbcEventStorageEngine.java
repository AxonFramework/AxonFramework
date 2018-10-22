/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.JdbcUtils;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.common.Assert.isTrue;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.jdbc.JdbcUtils.*;
import static org.axonframework.eventhandling.EventUtils.asDomainEventMessage;

/**
 * EventStorageEngine implementation that uses JDBC to store and fetch events.
 * <p>
 * By default the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class JdbcEventStorageEngine extends BatchingEventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(JpaEventStorageEngine.class);

    private static final int DEFAULT_MAX_GAP_OFFSET = 10000;
    private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;
    private static final int DEFAULT_GAP_TIMEOUT = 60000;
    private static final int DEFAULT_GAP_CLEANING_THRESHOLD = 250;

    private final ConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;
    private final Class<?> dataType;
    private final EventSchema schema;
    private final int maxGapOffset;
    private final long lowestGlobalSequence;
    private int gapTimeout;
    private int gapCleaningThreshold;

    /**
     * Instantiate a {@link JdbcEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link ConnectionProvider} and {@link TransactionManager} are not {@code null}, and will
     * throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JdbcEventStorageEngine} instance
     */
    protected JdbcEventStorageEngine(Builder builder) {
        super(builder);
        this.connectionProvider = builder.connectionProvider;
        this.transactionManager = builder.transactionManager;
        this.dataType = builder.dataType;
        this.schema = builder.schema;
        this.lowestGlobalSequence = builder.lowestGlobalSequence;
        this.maxGapOffset = builder.maxGapOffset;
        this.gapTimeout = builder.gapTimeout;
        this.gapCleaningThreshold = builder.gapCleaningThreshold;
    }

    /**
     * Instantiate a Builder to be able to create a {@link JdbcEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The snapshot {@link Serializer} defaults to {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to a {@link JdbcSQLErrorCodesResolver}</li>
     * <li>The event Serializer defaults to a {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@code snapshotFilter} defaults to a {@link Predicate} which returns {@code true} regardless.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@code dataType} is defaulted to the {@code byte[]} type.</li>
     * <li>The {@link EventSchema} defaults to an {@link EventSchema#EventSchema()} call.</li>
     * <li>The {@code maxGapOffset} defaults to an  integer of size {@code 10000}.</li>
     * <li>The {@code lowestGlobalSequence} defaults to a long of size {@code 1}.</li>
     * <li>The {@code gapTimeout} defaults to an integer of size {@code 60000} (1 minute).</li>
     * <li>The {@code gapCleaningThreshold} defaults to an integer of size {@code 250}.</li>
     * </ul>
     * <p>
     * The {@link ConnectionProvider} and {@link TransactionManager} are <b>hard requirements</b> and as such should
     * be provided.
     *
     * @return a Builder to be able to create a {@link JdbcEventStorageEngine}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Performs the DDL queries to create the schema necessary for this storage engine implementation.
     *
     * @param schemaFactory Factory of the event schema.
     * @throws EventStoreException when an error occurs executing SQL statements.
     */
    public void createSchema(EventTableFactory schemaFactory) {
        executeUpdates(getConnection(), e -> {
                           throw new EventStoreException("Failed to create event tables", e);
                       }, connection -> schemaFactory.createDomainEventTable(connection, schema),
                       connection -> schemaFactory.createSnapshotEventTable(connection, schema));
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (events.isEmpty()) {
            return;
        }
        final String table = schema.domainEventTable();
        final String sql = "INSERT INTO " + table + " (" +
                String.join(", ", schema.eventIdentifierColumn(), schema.aggregateIdentifierColumn(),
                            schema.sequenceNumberColumn(), schema.typeColumn(), schema.timestampColumn(),
                            schema.payloadTypeColumn(), schema.payloadRevisionColumn(), schema.payloadColumn(),
                            schema.metaDataColumn()) + ") VALUES (?,?,?,?,?,?,?,?,?)";

        transactionManager.executeInTransaction(
                () ->
                        executeBatch(getConnection(), connection -> {
                            PreparedStatement preparedStatement = connection.prepareStatement(sql);

                            for (EventMessage<?> eventMessage : events) {
                                DomainEventMessage<?> event = asDomainEventMessage(eventMessage);
                                SerializedObject<?> payload = event.serializePayload(serializer, dataType);
                                SerializedObject<?> metaData = event.serializeMetaData(serializer, dataType);
                                preparedStatement.setString(1, event.getIdentifier());
                                preparedStatement.setString(2, event.getAggregateIdentifier());
                                preparedStatement.setLong(3, event.getSequenceNumber());
                                preparedStatement.setString(4, event.getType());
                                writeTimestamp(preparedStatement, 5, event.getTimestamp());
                                preparedStatement.setString(6, payload.getType().getName());
                                preparedStatement.setString(7, payload.getType().getRevision());
                                preparedStatement.setObject(8, payload.getData());
                                preparedStatement.setObject(9, metaData.getData());
                                preparedStatement.addBatch();
                            }
                            return preparedStatement;
                        }, e -> handlePersistenceException(e, events.get(0))));
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        transactionManager.executeInTransaction(() -> {
            try {
                executeUpdates(
                        getConnection(), e -> handlePersistenceException(e, snapshot),
                        connection -> appendSnapshot(connection, snapshot, serializer),
                        connection -> deleteSnapshots(
                                connection, snapshot.getAggregateIdentifier(), snapshot.getSequenceNumber()
                        )
                );
            } catch (ConcurrencyException e) {
                // Ignore duplicate key issues in snapshot. It just means a snapshot already exists
            }
        });
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
        String sql = "SELECT max(" + schema.sequenceNumberColumn() + ") FROM " + schema.domainEventTable() +
                " WHERE " + schema.aggregateIdentifierColumn() + " = ?";
        return Optional.ofNullable(transactionManager.fetchInTransaction(
                () -> executeQuery(getConnection(), connection -> {
                                       PreparedStatement stmt = connection.prepareStatement(sql);
                                       stmt.setString(1, aggregateIdentifier);
                                       return stmt;
                                   },
                                   resultSet -> nextAndExtract(resultSet, 1, Long.class),
                                   e -> new EventStoreException(
                                           format("Failed to read events for aggregate [%s]", aggregateIdentifier), e
                                   )
                )));
    }

    @Override
    public TrackingToken createTailToken() {
        String sql = "SELECT min(" + schema.globalIndexColumn() + ") - 1 FROM " + schema.domainEventTable();
        Long index = transactionManager.fetchInTransaction(
                () -> executeQuery(getConnection(),
                                   connection -> connection.prepareStatement(sql),
                                   resultSet -> nextAndExtract(resultSet, 1, Long.class),
                                   e -> new EventStoreException("Failed to get tail token", e)));
        return Optional.ofNullable(index)
                       .map(seq -> GapAwareTrackingToken.newInstance(seq, Collections.emptySet()))
                       .orElse(null);
    }

    @Override
    public TrackingToken createHeadToken() {
        String sql = "SELECT max(" + schema.globalIndexColumn() + ") FROM " + schema.domainEventTable();
        Long index = transactionManager.fetchInTransaction(
                () -> executeQuery(getConnection(),
                                   connection -> connection.prepareStatement(sql),
                                   resultSet -> nextAndExtract(resultSet, 1, Long.class),
                                   e -> new EventStoreException("Failed to get head token", e)));
        return Optional.ofNullable(index)
                       .map(seq -> GapAwareTrackingToken.newInstance(seq, Collections.emptySet()))
                       .orElse(null);
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        String sql = "SELECT min(" + schema.globalIndexColumn() + ") - 1 FROM " + schema.domainEventTable() + " WHERE "
                + schema.timestampColumn() + " >= ?";
        Long index = transactionManager.fetchInTransaction(
                () -> executeQuery(getConnection(),
                                   connection -> {
                                       PreparedStatement stmt = connection.prepareStatement(sql);
                                       stmt.setString(1, formatInstant(dateTime));
                                       return stmt;
                                   },
                                   resultSet -> nextAndExtract(resultSet, 1, Long.class),
                                   e -> new EventStoreException(format("Failed to get token at [%s]", dateTime), e)));
        if (index == null) {
            return null;
        }
        return GapAwareTrackingToken.newInstance(index, Collections.emptySet());
    }

    /**
     * Creates a statement to append the given {@code snapshot} to the event storage using given {@code connection} to
     * the database. Use the given {@code serializer} to serialize the payload and metadata of the event.
     *
     * @param connection The connection to the database.
     * @param snapshot   The snapshot to append.
     * @param serializer The serializer that should be used when serializing the event's payload and metadata.
     * @return A {@link PreparedStatement} that appends the snapshot when executed.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected PreparedStatement appendSnapshot(Connection connection, DomainEventMessage<?> snapshot,
                                               Serializer serializer) throws SQLException {
        SerializedObject<?> payload = snapshot.serializePayload(serializer, dataType);
        SerializedObject<?> metaData = snapshot.serializeMetaData(serializer, dataType);
        final String sql = "INSERT INTO " + schema.snapshotTable() + " (" +
                String.join(", ", schema.eventIdentifierColumn(), schema.aggregateIdentifierColumn(),
                            schema.sequenceNumberColumn(), schema.typeColumn(), schema.timestampColumn(),
                            schema.payloadTypeColumn(), schema.payloadRevisionColumn(), schema.payloadColumn(),
                            schema.metaDataColumn()) + ") VALUES (?,?,?,?,?,?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql); // NOSONAR
        preparedStatement.setString(1, snapshot.getIdentifier());
        preparedStatement.setString(2, snapshot.getAggregateIdentifier());
        preparedStatement.setLong(3, snapshot.getSequenceNumber());
        preparedStatement.setString(4, snapshot.getType());
        writeTimestamp(preparedStatement, 5, snapshot.getTimestamp());
        preparedStatement.setString(6, payload.getType().getName());
        preparedStatement.setString(7, payload.getType().getRevision());
        preparedStatement.setObject(8, payload.getData());
        preparedStatement.setObject(9, metaData.getData());
        return preparedStatement;
    }

    /**
     * Creates a statement to delete all snapshots of the aggregate with given {@code aggregateIdentifier}.
     *
     * @param connection          The connection to the database.
     * @param aggregateIdentifier The identifier of the aggregate whose snapshots to delete.
     * @return A {@link PreparedStatement} that deletes all the aggregate's snapshots when executed.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement deleteSnapshots(Connection connection, String aggregateIdentifier, long sequenceNumber)
            throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM " + schema.snapshotTable() + " WHERE " + schema.aggregateIdentifierColumn() + " = ? "
                        + "AND " + schema.sequenceNumberColumn() + " < ?"
        );
        preparedStatement.setString(1, aggregateIdentifier);
        preparedStatement.setLong(2, sequenceNumber);
        return preparedStatement;
    }

    @Override
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return transactionManager.fetchInTransaction(
                () -> executeQuery(
                        getConnection(),
                        connection -> readEventData(connection, aggregateIdentifier, firstSequenceNumber, batchSize),
                        JdbcUtils.listResults(this::getDomainEventData),
                        e -> new EventStoreException(
                                format("Failed to read events for aggregate [%s]", aggregateIdentifier), e
                        )
                ));
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        isTrue(lastToken == null || lastToken instanceof GapAwareTrackingToken,
               () -> "Unsupported token format: " + lastToken);
        return transactionManager.fetchInTransaction(() -> {
            // If there are many gaps, it worthwhile checking if it is possible to clean them up.
            GapAwareTrackingToken cleanedToken;
            if (lastToken != null && ((GapAwareTrackingToken) lastToken).getGaps().size() > gapCleaningThreshold) {
                cleanedToken = cleanGaps(lastToken);
            } else {
                cleanedToken = (GapAwareTrackingToken) lastToken;
            }

            return executeQuery(
                    getConnection(),
                    connection -> readEventData(connection, cleanedToken, batchSize),
                    resultSet -> {
                        GapAwareTrackingToken previousToken = cleanedToken;
                        List<TrackedEventData<?>> results = new ArrayList<>();
                        while (resultSet.next()) {
                            TrackedEventData<?> next = getTrackedEventData(resultSet, previousToken);
                            results.add(next);
                            previousToken = (GapAwareTrackingToken) next.trackingToken();
                        }
                        return results;
                    },
                    e -> new EventStoreException(format("Failed to read events from token [%s]", lastToken), e)
            );
        });
    }

    private GapAwareTrackingToken cleanGaps(TrackingToken lastToken) {
        SortedSet<Long> gaps = ((GapAwareTrackingToken) lastToken).getGaps();
        return executeQuery(getConnection(), conn -> {
            PreparedStatement statement =
                    conn.prepareStatement(format("SELECT %s, %s FROM %s WHERE %s >= ? AND %s <= ?",
                                                 schema.globalIndexColumn(),
                                                 schema.timestampColumn(),
                                                 schema.domainEventTable(),
                                                 schema.globalIndexColumn(),
                                                 schema.globalIndexColumn()));
            statement.setLong(1, gaps.first());
            statement.setLong(2, gaps.last() + 1L);
            return statement;
        }, resultSet -> {
            GapAwareTrackingToken cleanToken = (GapAwareTrackingToken) lastToken;
            while (resultSet.next()) {
                try {
                    long sequenceNumber = resultSet.getLong(schema.globalIndexColumn());
                    Instant timestamp =
                            DateTimeUtils.parseInstant(readTimeStamp(resultSet, schema.timestampColumn()).toString());
                    if (gaps.contains(sequenceNumber) || timestamp.isAfter(gapTimeoutFrame())) {
                        // Filled a gap, should not continue cleaning up.
                        break;
                    }
                    if (gaps.contains(sequenceNumber - 1)) {
                        cleanToken = cleanToken.advanceTo(sequenceNumber - 1, maxGapOffset, false);
                    }
                } catch (DateTimeParseException e) {
                    logger.info("Unable to parse timestamp to clean old gaps. "
                                        + "Tokens may contain large numbers of gaps, decreasing Tracking performance.");
                    break;
                }
            }
            return cleanToken;
        }, e -> new EventStoreException(format("Failed to read events from token [%s]", lastToken), e));
    }

    @Override
    protected Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return transactionManager.fetchInTransaction(() -> {
            List<DomainEventData<?>> result =
                    executeQuery(getConnection(), connection -> readSnapshotData(connection, aggregateIdentifier),
                                 JdbcUtils.listResults(this::getSnapshotData), e -> new EventStoreException(
                                    format("Error reading aggregate snapshot [%s]", aggregateIdentifier), e));
            return result.stream();
        });
    }

    /**
     * Creates a statement to read domain event entries for an aggregate with given identifier starting with the first
     * entry having a sequence number that is equal or larger than the given {@code firstSequenceNumber}.
     *
     * @param connection          The connection to the database.
     * @param identifier          The identifier of the aggregate.
     * @param firstSequenceNumber The expected sequence number of the first returned entry.
     * @param batchSize           The number of items to include in the batch
     * @return A {@link PreparedStatement} that returns event entries for the given query when executed.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */

    protected PreparedStatement readEventData(Connection connection, String identifier,
                                              long firstSequenceNumber, int batchSize) throws SQLException {
        Transaction tx = transactionManager.startTransaction();
        try {
            final String sql = "SELECT " + trackedEventFields() + " FROM " + schema.domainEventTable() + " WHERE " +
                    schema.aggregateIdentifierColumn() + " = ? AND " + schema.sequenceNumberColumn() + " >= ? AND " +
                    schema.sequenceNumberColumn() + " < ? ORDER BY " + schema.sequenceNumberColumn() + " ASC";
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, identifier);
            preparedStatement.setLong(2, firstSequenceNumber);
            preparedStatement.setLong(3, firstSequenceNumber + batchSize);
            return preparedStatement;
        } finally {
            tx.commit();
        }
    }

    /**
     * Creates a statement to read tracked event entries stored since given tracking token. Pass a {@code trackingToken}
     * of {@code null} to create a statement for all entries in the storage.
     *
     * @param connection The connection to the database.
     * @param lastToken  Object describing the global index of the last processed event or {@code null} to return all
     *                   entries in the store.
     * @return A {@link PreparedStatement} that returns event entries for the given query when executed.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement readEventData(Connection connection, TrackingToken lastToken,
                                              int batchSize) throws SQLException {
        isTrue(lastToken == null || lastToken instanceof GapAwareTrackingToken,
               () -> format("Token [%s] is of the wrong type", lastToken));
        GapAwareTrackingToken previousToken = (GapAwareTrackingToken) lastToken;
        String sql = "SELECT " + trackedEventFields() + " FROM " + schema.domainEventTable() +
                " WHERE (" + schema.globalIndexColumn() + " > ? AND " + schema.globalIndexColumn() + " <= ?) ";
        List<Long> gaps;
        if (previousToken != null) {
            gaps = new ArrayList<>(previousToken.getGaps());
            if (!gaps.isEmpty()) {
                sql += " OR " + schema.globalIndexColumn() + " IN (" +
                        String.join(",", Collections.nCopies(gaps.size(), "?")) + ") ";
            }
        } else {
            gaps = Collections.emptyList();
        }
        sql += "ORDER BY " + schema.globalIndexColumn() + " ASC";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        long globalIndex = previousToken == null ? -1 : previousToken.getIndex();
        preparedStatement.setLong(1, globalIndex);
        preparedStatement.setLong(2, globalIndex + batchSize);
        for (int i = 0; i < gaps.size(); i++) {
            preparedStatement.setLong(i + 3, gaps.get(i));
        }
        return preparedStatement;
    }

    /**
     * Creates a statement to read the snapshot entry of an aggregate with given identifier.
     *
     * @param connection The connection to the database.
     * @param identifier The aggregate identifier.
     * @return A {@link PreparedStatement} that returns the last snapshot entry of the aggregate (if any) when executed.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement readSnapshotData(Connection connection, String identifier) throws SQLException {
        final String s = "SELECT " + domainEventFields() + " FROM " + schema.snapshotTable() + " WHERE " +
                schema.aggregateIdentifierColumn() + " = ? ORDER BY " + schema.sequenceNumberColumn() + " DESC";
        PreparedStatement statement = connection.prepareStatement(s);
        statement.setString(1, identifier);
        return statement;
    }

    /**
     * Extracts the next tracked event entry from the given {@code resultSet}.
     *
     * @param resultSet     The results of a query for tracked events.
     * @param previousToken The last known token of the tracker before obtaining this result set.
     * @return The next tracked event.
     *
     * @throws SQLException when an exception occurs while creating the event data.
     */
    protected TrackedEventData<?> getTrackedEventData(ResultSet resultSet,
                                                      GapAwareTrackingToken previousToken) throws SQLException {
        long globalSequence = resultSet.getLong(schema.globalIndexColumn());

        GenericDomainEventEntry<?> domainEvent = new GenericDomainEventEntry<>(
                resultSet.getString(schema.typeColumn()),
                resultSet.getString(schema.aggregateIdentifierColumn()),
                resultSet.getLong(schema.sequenceNumberColumn()),
                resultSet.getString(schema.eventIdentifierColumn()),
                readTimeStamp(resultSet, schema.timestampColumn()),
                resultSet.getString(schema.payloadTypeColumn()),
                resultSet.getString(schema.payloadRevisionColumn()),
                readPayload(resultSet, schema.payloadColumn()),
                readPayload(resultSet, schema.metaDataColumn())
        );

        // Now that we have the event itself, we can calculate the token.
        boolean allowGaps = domainEvent.getTimestamp().isAfter(gapTimeoutFrame());
        GapAwareTrackingToken token = previousToken;
        if (token == null) {
            token = GapAwareTrackingToken.newInstance(
                    globalSequence,
                    allowGaps
                            ? LongStream.range(Math.min(lowestGlobalSequence, globalSequence), globalSequence)
                                        .boxed()
                                        .collect(Collectors.toCollection(TreeSet::new))
                            : Collections.emptySortedSet()
            );
        } else {
            token = token.advanceTo(globalSequence, maxGapOffset, allowGaps);
        }
        return new TrackedDomainEventData<>(token, domainEvent);
    }

    private Instant gapTimeoutFrame() {
        return GenericEventMessage.clock.instant().minus(gapTimeout, ChronoUnit.MILLIS);
    }

    /**
     * Extracts the next domain event entry from the given {@code resultSet}.
     *
     * @param resultSet The results of a query for domain events of an aggregate.
     * @return The next domain event.
     *
     * @throws SQLException when an exception occurs while creating the event data.
     */
    protected DomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException {
        return new GenericDomainEventEntry<>(resultSet.getString(schema.typeColumn()),
                                             resultSet.getString(schema.aggregateIdentifierColumn()),
                                             resultSet.getLong(schema.sequenceNumberColumn()),
                                             resultSet.getString(schema.eventIdentifierColumn()),
                                             readTimeStamp(resultSet, schema.timestampColumn()),
                                             resultSet.getString(schema.payloadTypeColumn()),
                                             resultSet.getString(schema.payloadRevisionColumn()),
                                             readPayload(resultSet, schema.payloadColumn()),
                                             readPayload(resultSet, schema.metaDataColumn()));
    }

    /**
     * Extracts the next snapshot entry from the given {@code resultSet}.
     *
     * @param resultSet The results of a query for a snapshot of an aggregate.
     * @return The next snapshot data.
     *
     * @throws SQLException when an exception occurs while creating the event data.
     */
    protected DomainEventData<?> getSnapshotData(ResultSet resultSet) throws SQLException {
        return new GenericDomainEventEntry<>(resultSet.getString(schema.typeColumn()),
                                             resultSet.getString(schema.aggregateIdentifierColumn()),
                                             resultSet.getLong(schema.sequenceNumberColumn()),
                                             resultSet.getString(schema.eventIdentifierColumn()),
                                             readTimeStamp(resultSet, schema.timestampColumn()),
                                             resultSet.getString(schema.payloadTypeColumn()),
                                             resultSet.getString(schema.payloadRevisionColumn()),
                                             readPayload(resultSet, schema.payloadColumn()),
                                             readPayload(resultSet, schema.metaDataColumn()));
    }

    /**
     * Reads a timestamp from the given {@code resultSet} at given {@code columnIndex}. The resultSet is
     * positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet  The resultSet containing the stored data.
     * @param columnName The name of the column containing the timestamp.
     * @return an object describing the timestamp.
     *
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    /**
     * Write a timestamp from a {@link Instant} to a data value suitable for the database scheme.
     *
     * @param preparedStatement the statement to update.
     * @param position          the position of the timestamp parameter in the statement.
     * @param timestamp         {@link Instant} to convert.
     * @throws SQLException if modification of the statement fails.
     */
    protected void writeTimestamp(PreparedStatement preparedStatement, int position,
                                  Instant timestamp) throws SQLException {
        preparedStatement.setString(position, formatInstant(timestamp));
    }

    /**
     * Reads a serialized object from the given {@code resultSet} at given {@code columnIndex}. The resultSet
     * is positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet  The resultSet containing the stored data.
     * @param columnName The name of the column containing the payload.
     * @return an object describing the serialized data.
     *
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    @SuppressWarnings("unchecked")
    protected <T> T readPayload(ResultSet resultSet, String columnName) throws SQLException {
        if (byte[].class.equals(dataType)) {
            return (T) resultSet.getBytes(columnName);
        }
        return (T) resultSet.getObject(columnName);
    }

    /**
     * Returns a comma separated list of domain event column names to select from an event or snapshot entry.
     *
     * @return comma separated domain event column names.
     */
    protected String domainEventFields() {
        return String.join(", ", schema.eventIdentifierColumn(), schema.timestampColumn(), schema.payloadTypeColumn(),
                           schema.payloadRevisionColumn(), schema.payloadColumn(), schema.metaDataColumn(),
                           schema.typeColumn(), schema.aggregateIdentifierColumn(), schema.sequenceNumberColumn());
    }

    /**
     * Returns a comma separated list of tracked domain event column names to select from an event entry.
     *
     * @return comma separated tracked domain event column names.
     */
    protected String trackedEventFields() {
        return schema.globalIndexColumn() + ", " + domainEventFields();
    }

    /**
     * Returns the {@link EventSchema} that defines the table and column names of event tables in the database.
     *
     * @return the event schema.
     */
    protected EventSchema schema() {
        return schema;
    }

    /**
     * Returns a {@link Connection} to the database.
     *
     * @return a database Connection.
     */
    protected Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new EventStoreException("Failed to obtain a database connection", e);
        }
    }

    /**
     * Sets the amount of time until a 'gap' in a TrackingToken may be considered timed out. This setting will affect
     * the cleaning process of gaps. Gaps that have timed out will be removed from Tracking Tokens to improve
     * performance of reading events. Defaults to 60000 (1 minute).
     *
     * @param gapTimeout The amount of time, in milliseconds until a gap may be considered timed out.
     */
    public void setGapTimeout(int gapTimeout) {
        this.gapTimeout = gapTimeout;
    }

    /**
     * Sets the threshold of number of gaps in a token before an attempt to clean gaps up is taken. Defaults to 250.
     *
     * @param gapCleaningThreshold The number of gaps before triggering a cleanup.
     */
    public void setGapCleaningThreshold(int gapCleaningThreshold) {
        this.gapCleaningThreshold = gapCleaningThreshold;
    }

    /**
     * Builder class to instantiate a {@link JdbcEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The snapshot {@link Serializer} defaults to {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to a {@link JdbcSQLErrorCodesResolver}</li>
     * <li>The event Serializer defaults to a {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@code snapshotFilter} defaults to a {@link Predicate} which returns {@code true} regardless.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@code dataType} is defaulted to the {@code byte[]} type.</li>
     * <li>The {@link EventSchema} defaults to an {@link EventSchema#EventSchema()} call.</li>
     * <li>The {@code maxGapOffset} defaults to an  integer of size {@code 10000}.</li>
     * <li>The {@code lowestGlobalSequence} defaults to a long of size {@code 1}.</li>
     * <li>The {@code gapTimeout} defaults to an integer of size {@code 60000} (1 minute).</li>
     * <li>The {@code gapCleaningThreshold} defaults to an integer of size {@code 250}.</li>
     * </ul>
     * <p>
     * The {@link ConnectionProvider} and {@link TransactionManager} are <b>hard requirements</b> and as such should
     * be provided.
     */
    public static class Builder extends BatchingEventStorageEngine.Builder {

        private ConnectionProvider connectionProvider;
        private TransactionManager transactionManager;
        private Class<?> dataType = byte[].class;
        private EventSchema schema = new EventSchema();
        private int maxGapOffset = DEFAULT_MAX_GAP_OFFSET;
        private long lowestGlobalSequence = DEFAULT_LOWEST_GLOBAL_SEQUENCE;
        private int gapTimeout = DEFAULT_GAP_TIMEOUT;
        private int gapCleaningThreshold = DEFAULT_GAP_CLEANING_THRESHOLD;

        private Builder() {
            persistenceExceptionResolver(new JdbcSQLErrorCodesResolver());
        }

        @Override
        public Builder snapshotSerializer(Serializer snapshotSerializer) {
            super.snapshotSerializer(snapshotSerializer);
            return this;
        }

        @Override
        public Builder upcasterChain(EventUpcaster upcasterChain) {
            super.upcasterChain(upcasterChain);
            return this;
        }

        /**
         * {@inheritDoc} Defaults to a {@link JdbcSQLErrorCodesResolver}.
         */
        @Override
        public Builder persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            super.persistenceExceptionResolver(persistenceExceptionResolver);
            return this;
        }

        @Override
        public Builder eventSerializer(Serializer eventSerializer) {
            super.eventSerializer(eventSerializer);
            return this;
        }

        @Override
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        @Override
        public Builder batchSize(int batchSize) {
            super.batchSize(batchSize);
            return this;
        }

        /**
         * Sets the {@link ConnectionProvider} which provides access to a JDBC connection.
         *
         * @param connectionProvider a {@link ConnectionProvider} which provides access to a JDBC connection
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder connectionProvider(ConnectionProvider connectionProvider) {
            assertNonNull(connectionProvider, "ConnectionProvider may not be null");
            this.connectionProvider = connectionProvider;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transactions around fetching event data. Required by
         * certain databases for reading blob data.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transactions around fetching event data
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@code dataType} specifying the serialized type of the Event Message's payload and Meta Data.
         * Defaults to the {@code byte[]} {@link Class}.
         *
         * @param dataType a {@link Class} specifying the serialized type of the Event Message's payload and Meta Data
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dataType(Class<?> dataType) {
            assertNonNull(dataType, "dataType may not be null");
            this.dataType = dataType;
            return this;
        }

        /**
         * Sets the {@link EventSchema} describing the database schema of event entries. Defaults to
         * {@link EventSchema#EventSchema()}.
         *
         * @param schema the {@link EventSchema} describing the database schema of event entries
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder schema(EventSchema schema) {
            assertNonNull(schema, "EventSchema may not be null");
            this.schema = schema;
            return this;
        }

        /**
         * Sets the {@code maxGapOffset} specifying the maximum distance in sequence numbers between a missing event and
         * the event with the highest known index. If the gap is bigger it is assumed that the missing event will not be
         * committed to the store anymore. This event storage engine will no longer look for those events the next time
         * a batch is fetched. Defaults to an integer of {@code 10000}
         * ({@link JdbcEventStorageEngine#DEFAULT_MAX_GAP_OFFSET}.
         *
         * @param maxGapOffset an {@code int} specifying the maximum distance in sequence numbers between a missing
         *                     event and the event with the highest known index
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder maxGapOffset(int maxGapOffset) {
            assertPositive(maxGapOffset, "maxGapOffset");
            this.maxGapOffset = maxGapOffset;
            return this;
        }

        /**
         * Sets the {@code lowestGlobalSequence} specifying the first expected auto generated sequence number. For most
         * data stores this is 1 unless the table has contained entries before. Defaults to a {@code long} of {@code 1}
         * ({@link JdbcEventStorageEngine#DEFAULT_LOWEST_GLOBAL_SEQUENCE}).
         *
         * @param lowestGlobalSequence a {@code long} specifying the first expected auto generated sequence number
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder lowestGlobalSequence(long lowestGlobalSequence) {
            assertThat(lowestGlobalSequence,
                       number -> number > 0,
                       "The lowestGlobalSequence must be a positive number");
            this.lowestGlobalSequence = lowestGlobalSequence;
            return this;
        }

        /**
         * Sets the amount of time until a 'gap' in a TrackingToken may be considered timed out. This setting will
         * affect the cleaning process of gaps. Gaps that have timed out will be removed from Tracking Tokens to improve
         * performance of reading events. Defaults to an integer of {@code 60000}
         * ({@link JdbcEventStorageEngine#DEFAULT_GAP_TIMEOUT}), thus 1 minute.
         *
         * @param gapTimeout an {@code int} specifying the amount of time until a 'gap' in a TrackingToken may be
         *                   considered timed out
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder gapTimeout(int gapTimeout) {
            assertPositive(gapTimeout, "gapTimeout");
            this.gapTimeout = gapTimeout;
            return this;
        }

        /**
         * Sets the threshold of number of gaps in a token before an attempt to clean gaps up is taken. Defaults to an
         * integer of {@code 250} ({@link JdbcEventStorageEngine#DEFAULT_GAP_CLEANING_THRESHOLD}).
         *
         * @param gapCleaningThreshold an {@code int} specifying the threshold of number of gaps in a token before an
         *                             attempt to clean gaps up is taken
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder gapCleaningThreshold(int gapCleaningThreshold) {
            assertPositive(gapCleaningThreshold, "gapCleaningThreshold");
            this.gapCleaningThreshold = gapCleaningThreshold;
            return this;
        }

        private void assertPositive(int num, final String numberDescription) {
            assertThat(num, number -> number > 0, "The " + numberDescription + " must be a positive number");
        }

        /**
         * Initializes a {@link JdbcEventStorageEngine} as specified through this Builder.
         *
         * @return a {@link JdbcEventStorageEngine} as specified through this Builder
         */
        public JdbcEventStorageEngine build() {
            return new JdbcEventStorageEngine(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(connectionProvider, "The ConnectionProvider is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
