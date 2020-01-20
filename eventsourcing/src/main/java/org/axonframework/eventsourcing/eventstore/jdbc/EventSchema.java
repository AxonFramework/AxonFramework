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

import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.TrackingToken;

import java.sql.Connection;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Schema of an event entry to be stored using Jdbc.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class EventSchema {

    private final String domainEventTable, snapshotTable, globalIndexColumn, timestampColumn, eventIdentifierColumn,
            aggregateIdentifierColumn, sequenceNumberColumn, typeColumn, payloadTypeColumn, payloadRevisionColumn,
            payloadColumn, metaDataColumn;

    private final Function<EventSchema, String> domainEventFields, trackedEventFields;

    private final Function<EventSchema, String> createTokenAtSqlStatement, appendEventsSqlStatement,
            lastSequenceNumberForSqlStatement, createTailTokenSqlStatement, createHeadTokenSqlStatement,
            appendSnapshotSqlStatement, deleteSnapshotsSqlStatement, fetchTrackedEventsSqlStatement,
            cleanGapsSqlStatement, readEventDataForAggregateSqlStatement, readSnapshotDataSqlStatement,
            readEventDataWithoutGapsSqlStatement;

    private final BiFunction<EventSchema, Integer, String> readEventDataWithGapsSqlStatement;

    /**
     * Initializes the default Event Schema
     */
    public EventSchema() {
        this(builder());
    }

    private EventSchema(Builder builder) {
        domainEventTable = builder.domainEventTable;
        snapshotTable = builder.snapshotTable;
        globalIndexColumn = builder.globalIndexColumn;
        timestampColumn = builder.timestampColumn;
        eventIdentifierColumn = builder.eventIdentifierColumn;
        aggregateIdentifierColumn = builder.aggregateIdentifierColumn;
        sequenceNumberColumn = builder.sequenceNumberColumn;
        typeColumn = builder.typeColumn;
        payloadTypeColumn = builder.payloadTypeColumn;
        payloadRevisionColumn = builder.payloadRevisionColumn;
        payloadColumn = builder.payloadColumn;
        metaDataColumn = builder.metaDataColumn;
        domainEventFields = builder.domainEventFields;
        trackedEventFields = builder.trackedEventFields;
        createTokenAtSqlStatement = builder.createTokenAtSqlStatement;
        appendEventsSqlStatement = builder.appendEventsSqlStatement;
        lastSequenceNumberForSqlStatement = builder.lastSequenceNumberForSqlStatement;
        createTailTokenSqlStatement = builder.createTailTokenSqlStatement;
        createHeadTokenSqlStatement = builder.createHeadTokenSqlStatement;
        appendSnapshotSqlStatement = builder.appendSnapshotSqlStatement;
        deleteSnapshotsSqlStatement = builder.deleteSnapshotsSqlStatement;
        fetchTrackedEventsSqlStatement = builder.fetchTrackedEventsSqlStatement;
        cleanGapsSqlStatement = builder.cleanGapsSqlStatement;
        readEventDataForAggregateSqlStatement = builder.readEventDataForAggregateSqlStatement;
        readSnapshotDataSqlStatement = builder.readSnapshotDataSqlStatement;
        readEventDataWithoutGapsSqlStatement = builder.readEventDataWithoutGapsSqlStatement;
        readEventDataWithGapsSqlStatement = builder.readEventDataWithGapsSqlStatement;
    }

    /**
     * Returns a new {@link Builder} initialized with default settings.
     *
     * @return a new builder for the event eventSchema
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of the domain event table.
     *
     * @return the name of the domain event table
     */
    public String domainEventTable() {
        return domainEventTable;
    }

    /**
     * Returns the name of the snapshot event table.
     *
     * @return the name of the snapshot event table
     */
    public String snapshotTable() {
        return snapshotTable;
    }

    /**
     * Get the name of the column containing the global index of the event.
     *
     * @return the name of the column containing the global index
     */
    public String globalIndexColumn() {
        return globalIndexColumn;
    }

    /**
     * Get the name of the column containing the timestamp of the event.
     *
     * @return the name of the column containing the timestamp
     */
    public String timestampColumn() {
        return timestampColumn;
    }

    /**
     * Get the name of the column containing the identifier of the event.
     *
     * @return the name of the column containing the event identifier
     */
    public String eventIdentifierColumn() {
        return eventIdentifierColumn;
    }

    /**
     * Get the name of the column containing the aggregate identifier of the event.
     *
     * @return the name of the column containing the aggregate identifier
     */
    public String aggregateIdentifierColumn() {
        return aggregateIdentifierColumn;
    }

    /**
     * Get the name of the column containing the aggregate sequence number of the event.
     *
     * @return the name of the column containing the aggregate sequence number
     */
    public String sequenceNumberColumn() {
        return sequenceNumberColumn;
    }

    /**
     * Get the name of the column containing the aggregate type of the event.
     *
     * @return the name of the column containing the aggregate type
     */
    public String typeColumn() {
        return typeColumn;
    }

    /**
     * Get the name of the column containing the event payload type.
     *
     * @return the name of the column containing the payload type
     */
    public String payloadTypeColumn() {
        return payloadTypeColumn;
    }

    /**
     * Get the name of the column containing the revision number of the serialized payload.
     *
     * @return the name of the column containing the revision number of the serialized payload
     */
    public String payloadRevisionColumn() {
        return payloadRevisionColumn;
    }

    /**
     * Get the name of the column containing the serialized payload of the event.
     *
     * @return the name of the column containing the serialized payload
     */
    public String payloadColumn() {
        return payloadColumn;
    }

    /**
     * Get the name of the column containing the serialized metadata of the event.
     *
     * @return the name of the column containing the serialized metadata
     */
    public String metaDataColumn() {
        return metaDataColumn;
    }

    /**
     * Get a comma separated list of domain event column names to select from an event or snapshot entry.
     *
     * @return comma separated domain event column names.
     */
    public String domainEventFields() {
        return domainEventFields.apply(this);
    }

    /**
     * Get a comma separated list of tracked domain event column names to select from an event entry.
     *
     * @return comma separated tracked domain event column names.
     */
    public String trackedEventFields() {
        return trackedEventFields.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#createTokenAt}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#createTokenAt}.
     */
    public String createTokenAtSqlStatement() {
        return createTokenAtSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#appendEvents}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#appendEvents}.
     */
    public String appendEventsSqlStatement() {
        return appendEventsSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#lastSequenceNumberFor}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#lastSequenceNumberFor}.
     */
    public String lastSequenceNumberForSqlStatement() {
        return lastSequenceNumberForSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#createTailToken}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#createTailToken}.
     */
    public String createTailTokenSqlStatement() {
        return createTailTokenSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#createHeadToken}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#createHeadToken}.
     */
    public String createHeadTokenSqlStatement() {
        return createHeadTokenSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#appendSnapshot}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#appendSnapshot}.
     */
    public String appendSnapshotSqlStatement() {
        return appendSnapshotSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#deleteSnapshots}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#deleteSnapshots}.
     */
    public String deleteSnapshotsSqlStatement() {
        return deleteSnapshotsSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#fetchTrackedEvents}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#fetchTrackedEvents}.
     */
    public String fetchTrackedEventsSqlStatement() {
        return fetchTrackedEventsSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at internal clean gaps operation.
     *
     * @return the SqlStatement to be used at internal clean gaps operation.
     */
    public String cleanGapsSqlStatement() {
        return cleanGapsSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, String, long, int)}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, String, long,
     * int)}.
     */
    public String readEventDataForAggregateSqlStatement() {
        return readEventDataForAggregateSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#readSnapshotData}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#readSnapshotData}.
     */
    public String readSnapshotDataSqlStatement() {
        return readSnapshotDataSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken, int)}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken,
     * int)}.
     */
    public String readEventDataWithoutGapsSqlStatement() {
        return readEventDataWithoutGapsSqlStatement.apply(this);
    }

    /**
     * Get the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken, int)}.
     *
     * @return the SqlStatement to be used at {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken,
     * int)}.
     */
    public String readEventDataWithGapsSqlStatement(Integer gapSize) {
        return readEventDataWithGapsSqlStatement.apply(this, gapSize);
    }

    /**
     * Builder for an {@link EventSchema} that gets initialized with default values.
     */
    @SuppressWarnings("SqlResolve")
    public static class Builder {

        private String domainEventTable = "DomainEventEntry";
        private String snapshotTable = "SnapshotEventEntry";
        private String globalIndexColumn = "globalIndex";
        private String timestampColumn = "timeStamp";
        private String eventIdentifierColumn = "eventIdentifier";
        private String aggregateIdentifierColumn = "aggregateIdentifier";
        private String sequenceNumberColumn = "sequenceNumber";
        private String typeColumn = "type";
        private String payloadTypeColumn = "payloadType";
        private String payloadRevisionColumn = "payloadRevision";
        private String payloadColumn = "payload";
        private String metaDataColumn = "metaData";

        private Function<EventSchema, String> domainEventFields = eventSchema ->
                String.join(", ",
                            eventSchema.eventIdentifierColumn(),
                            eventSchema.aggregateIdentifierColumn(),
                            eventSchema.sequenceNumberColumn(),
                            eventSchema.typeColumn(),
                            eventSchema.timestampColumn(),
                            eventSchema.payloadTypeColumn(),
                            eventSchema.payloadRevisionColumn(),
                            eventSchema.payloadColumn(),
                            eventSchema.metaDataColumn());

        private Function<EventSchema, String> trackedEventFields = eventSchema ->
                eventSchema.globalIndexColumn() + ", " + eventSchema.domainEventFields();

        private Function<EventSchema, String> createTokenAtSqlStatement = eventSchema -> "SELECT min("
                + eventSchema.globalIndexColumn() + ") - 1 FROM " + eventSchema.domainEventTable() + " WHERE "
                + eventSchema.timestampColumn() + " >= ?";

        private Function<EventSchema, String> appendEventsSqlStatement = eventSchema -> "INSERT INTO "
                + eventSchema.domainEventTable() + " (" + eventSchema.domainEventFields()
                + ") VALUES (?,?,?,?,?,?,?,?,?)";

        private Function<EventSchema, String> lastSequenceNumberForSqlStatement = eventSchema -> "SELECT max("
                + eventSchema.sequenceNumberColumn() + ") FROM " + eventSchema.domainEventTable() + " WHERE "
                + eventSchema.aggregateIdentifierColumn() + " = ?";

        private Function<EventSchema, String> createTailTokenSqlStatement = eventSchema -> "SELECT min("
                + eventSchema.globalIndexColumn() + ") - 1 FROM " + eventSchema.domainEventTable();

        private Function<EventSchema, String> createHeadTokenSqlStatement = eventSchema -> "SELECT max("
                + eventSchema.globalIndexColumn() + ") FROM " + eventSchema.domainEventTable();

        private Function<EventSchema, String> appendSnapshotSqlStatement = eventSchema -> "INSERT INTO "
                + eventSchema.snapshotTable() + " (" + eventSchema.domainEventFields() + ") VALUES (?,?,?,?,?,?,?,?,?)";

        private Function<EventSchema, String> deleteSnapshotsSqlStatement = eventSchema -> "DELETE FROM "
                + eventSchema.snapshotTable() + " WHERE " + eventSchema.aggregateIdentifierColumn() + " = ? AND "
                + eventSchema.sequenceNumberColumn() + " < ?";

        private Function<EventSchema, String> fetchTrackedEventsSqlStatement = eventSchema -> "SELECT min("
                + eventSchema.globalIndexColumn() + ") FROM " + eventSchema.domainEventTable() + " WHERE "
                + eventSchema.globalIndexColumn() + " > ?";

        private Function<EventSchema, String> cleanGapsSqlStatement = eventSchema -> "SELECT "
                + eventSchema.globalIndexColumn() + ", " + eventSchema.timestampColumn() + " FROM " + eventSchema
                .domainEventTable() + " WHERE " + eventSchema.globalIndexColumn() + " >= ? AND " + eventSchema
                .globalIndexColumn() + " <= ?";

        private Function<EventSchema, String> readEventDataForAggregateSqlStatement = eventSchema -> "SELECT "
                + eventSchema.trackedEventFields() + " FROM " + eventSchema.domainEventTable() + " WHERE "
                + eventSchema.aggregateIdentifierColumn() + " = ? AND " + eventSchema.sequenceNumberColumn()
                + " >= ? AND " + eventSchema.sequenceNumberColumn() + " < ? ORDER BY "
                + eventSchema.sequenceNumberColumn() + " ASC";

        private Function<EventSchema, String> readSnapshotDataSqlStatement = eventSchema -> "SELECT "
                + eventSchema.domainEventFields() + " FROM " + eventSchema.snapshotTable() + " WHERE "
                + eventSchema.aggregateIdentifierColumn() + " = ? ORDER BY " + eventSchema.sequenceNumberColumn()
                + " DESC";

        private Function<EventSchema, String> readEventDataWithoutGapsSqlStatement = eventSchema -> "SELECT "
                + eventSchema.trackedEventFields() + " FROM " + eventSchema.domainEventTable() + " WHERE ("
                + eventSchema.globalIndexColumn() + " > ? AND " + eventSchema.globalIndexColumn()
                + " <= ?) ORDER BY " + eventSchema.globalIndexColumn() + " ASC";

        private BiFunction<EventSchema, Integer, String> readEventDataWithGapsSqlStatement = (eventSchema, gapSize) ->
                "SELECT " + eventSchema.trackedEventFields() + " FROM " + eventSchema.domainEventTable() +
                        " WHERE (" + eventSchema.globalIndexColumn() + " > ? AND " + eventSchema.globalIndexColumn()
                        + " <= ?) OR " + eventSchema.globalIndexColumn() + " IN (" +
                        String.join(",", Collections.nCopies(gapSize, "?")) + ") "
                        + "ORDER BY " + eventSchema.globalIndexColumn() + " ASC";

        /**
         * Sets the name of the domain events table. Defaults to 'DomainEventEntry'.
         *
         * @param eventTable the event table name
         * @return the modified Builder instance
         */
        public Builder eventTable(String eventTable) {
            this.domainEventTable = eventTable;
            return this;
        }

        /**
         * Sets the name of the snapshot events table. Defaults to 'SnapshotEventEntry'.
         *
         * @param snapshotTable the snapshot table name
         * @return the modified Builder instance
         */
        public Builder snapshotTable(String snapshotTable) {
            this.snapshotTable = snapshotTable;
            return this;
        }

        /**
         * Sets the name of the global index column. Defaults to 'globalIndex'.
         *
         * @param globalIndexColumn the name of the global index column.
         * @return the modified Builder instance
         */
        public Builder globalIndexColumn(String globalIndexColumn) {
            this.globalIndexColumn = globalIndexColumn;
            return this;
        }

        /**
         * Sets the name of the timestamp column. Defaults to 'timeStamp'.
         *
         * @param timestampColumn the name of the timestamp column.
         * @return the modified Builder instance
         */
        public Builder timestampColumn(String timestampColumn) {
            this.timestampColumn = timestampColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'eventIdentifier'.
         *
         * @param eventIdentifierColumn the name of the event identifier column.
         * @return the modified Builder instance
         */
        public Builder eventIdentifierColumn(String eventIdentifierColumn) {
            this.eventIdentifierColumn = eventIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'aggregateIdentifier'.
         *
         * @param aggregateIdentifierColumn the name of the aggregate identifier column.
         * @return the modified Builder instance
         */
        public Builder aggregateIdentifierColumn(String aggregateIdentifierColumn) {
            this.aggregateIdentifierColumn = aggregateIdentifierColumn;
            return this;
        }

        /**
         * Sets the name of the event identifier column. Defaults to 'sequenceNumber'.
         *
         * @param sequenceNumberColumn the name of the sequence number column.
         * @return the modified Builder instance
         */
        public Builder sequenceNumberColumn(String sequenceNumberColumn) {
            this.sequenceNumberColumn = sequenceNumberColumn;
            return this;
        }

        /**
         * Sets the name of the aggregate type column. Defaults to 'type'.
         *
         * @param typeColumn the name of the aggregate type column.
         * @return the modified Builder instance
         */
        public Builder typeColumn(String typeColumn) {
            this.typeColumn = typeColumn;
            return this;
        }

        /**
         * Sets the name of the event payload type column. Defaults to 'payloadType'.
         *
         * @param payloadTypeColumn the name of the payload type column.
         * @return the modified Builder instance
         */
        public Builder payloadTypeColumn(String payloadTypeColumn) {
            this.payloadTypeColumn = payloadTypeColumn;
            return this;
        }

        /**
         * Sets the name of the event payload revision column. Defaults to 'payloadRevision'.
         *
         * @param payloadRevisionColumn the name of the payload revision column.
         * @return the modified Builder instance
         */
        public Builder payloadRevisionColumn(String payloadRevisionColumn) {
            this.payloadRevisionColumn = payloadRevisionColumn;
            return this;
        }

        /**
         * Sets the name of the event payload column. Defaults to 'payload'.
         *
         * @param payloadColumn the name of the payload column.
         * @return the modified Builder instance
         */
        public Builder payloadColumn(String payloadColumn) {
            this.payloadColumn = payloadColumn;
            return this;
        }

        /**
         * Sets the name of the event metadata column. Defaults to 'metaData'.
         *
         * @param metaDataColumn the name of the metadata column.
         * @return the modified Builder instance
         */
        public Builder metaDataColumn(String metaDataColumn) {
            this.metaDataColumn = metaDataColumn;
            return this;
        }

        /**
         * Set a comma separated list of domain event column names to select from an event or snapshot entry. Defaults
         * to:
         * <p/>
         * {@code "[eventIdentifierColumn], [aggregateIdentifierColumn], [sequenceNumberColumn], [typeColumn],
         * [timestampColumn], [payloadTypeColumn], [payloadRevisionColumn], [payloadColumn], [metaDataColumn]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder domainEventFields(Function<EventSchema, String> domainEventFields) {
            this.domainEventFields = domainEventFields;
            return this;
        }

        /**
         * Set a comma separated list of tracked domain event column names to select from an event entry. Defaults to:
         * <p/>
         * {@code "[globalIndexColumn], [domainEventFields]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder trackedEventFields(Function<EventSchema, String> trackedEventFields) {
            this.trackedEventFields = trackedEventFields;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#createTokenAt}. Defaults to:
         * <p/>
         * {@code "SELECT min([globalIndexColumn]) - 1 FROM [domainEventTable] WHERE [timestampColumn] >= ?" }
         * <p/>
         * <b>NOTE:</b> "?" is the Instant parameter from {@link JdbcEventStorageEngine#createTokenAt} and should
         * <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder createTokenAtSqlStatement(Function<EventSchema, String> createTokenAtSqlStatement) {
            this.createTokenAtSqlStatement = createTokenAtSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#appendEvents}. Defaults to:
         * <p/>
         * {@code "INSERT INTO [domainEventTable] ([domainEventFields]) VALUES (?,?,?,?,?,?,?,?,?)" }
         * <p/>
         * <b>NOTE:</b> each "?" is a domain event field from {@link EventSchema#domainEventFields()} and should
         * <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder appendEventsSqlStatement(Function<EventSchema, String> appendEventsSqlStatement) {
            this.appendEventsSqlStatement = appendEventsSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#lastSequenceNumberFor}. Defaults to:
         * <p/>
         * {@code "SELECT max([sequenceNumberColumn]) FROM [domainEventTable] WHERE [aggregateIdentifierColumn] = ?" }
         * <p/>
         * <b>NOTE:</b> "?" is the aggregateIdentifier parameter from {@link JdbcEventStorageEngine#lastSequenceNumberFor}
         * and should <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder lastSequenceNumberForSqlStatement(
                Function<EventSchema, String> lastSequenceNumberForSqlStatement) {
            this.lastSequenceNumberForSqlStatement = lastSequenceNumberForSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#createTailToken}. Defaults to:
         * <p/>
         * {@code "SELECT min([globalIndexColumn]) - 1 FROM [domainEventTable]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder createTailTokenSqlStatement(Function<EventSchema, String> createTailTokenSqlStatement) {
            this.createTailTokenSqlStatement = createTailTokenSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#createHeadToken}. Defaults to:
         * <p/>
         * {@code "SELECT max([globalIndexColumn]) FROM [domainEventTable]" }
         * <p/>
         *
         * @return the modified Builder instance
         */
        public Builder createHeadTokenSqlStatement(Function<EventSchema, String> createHeadTokenSqlStatement) {
            this.createHeadTokenSqlStatement = createHeadTokenSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#appendSnapshot}. Defaults to:
         * <p/>
         * {@code "INSERT INTO [snapshotTable] ([domainEventFields]) VALUES (?,?,?,?,?,?,?,?,?)" }
         * <p/>
         * <b>NOTE:</b> each "?" is a domain event field from {@link EventSchema#domainEventFields()} and should
         * <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder appendSnapshotSqlStatement(Function<EventSchema, String> appendSnapshotSqlStatement) {
            this.appendSnapshotSqlStatement = appendSnapshotSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#deleteSnapshots}. Defaults to:
         * <p/>
         * {@code "DELETE FROM [snapshotTable] WHERE [aggregateIdentifierColumn] = ?1 AND [sequenceNumberColumn] < ?2"
         * }
         * <p/>
         * <b>NOTE:</b> "?1" is the aggregateIdentifier and "?2" is the sequenceNumber parameters from {@link
         * JdbcEventStorageEngine#deleteSnapshots} and they should <b>always</b> be present for the SqlStatement to
         * work.
         *
         * @return the modified Builder instance
         */
        public Builder deleteSnapshotsSqlStatement(Function<EventSchema, String> deleteSnapshotsSqlStatement) {
            this.deleteSnapshotsSqlStatement = deleteSnapshotsSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#fetchTrackedEvents}. Defaults to:
         * <p/>
         * {@code "SELECT min([globalIndexColumn]) FROM [domainEventTable] WHERE [globalIndexColumn] > ?" }
         * <p/>
         * <b>NOTE:</b> "?" is based on the lastToken parameter from {@link JdbcEventStorageEngine#fetchTrackedEvents}
         * and should <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder fetchTrackedEventsSqlStatement(Function<EventSchema, String> fetchTrackedEventsSqlStatement) {
            this.fetchTrackedEventsSqlStatement = fetchTrackedEventsSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on internal cleanGaps operation. Defaults to:
         * <p/>
         * {@code "SELECT [globalIndexColumn], [timestampColumn] FROM [domainEventTable] WHERE [globalIndexColumn] >= ?1
         * AND [globalIndexColumn] <= ?2" }
         * <p/>
         * <b>NOTE:</b> "?1" and "?2" are taken from the {@link GapAwareTrackingToken#getGaps()} first and last.
         *
         * @return the modified Builder instance
         */
        public Builder cleanGapsSqlStatement(Function<EventSchema, String> cleanGapsSqlStatement) {
            this.cleanGapsSqlStatement = cleanGapsSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#readEventData(Connection, String, long,
         * int)} a} for aggregate. Defaults to:
         * <p/>
         * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE [aggregateIdentifierColumn] = ?1 AND
         * [sequenceNumberColumn] >= ?2 AND [sequenceNumberColumn] < ?3 ORDER BY [sequenceNumberColumn] ASC" }
         * <p/>
         * <b>NOTE:</b> "?1" is the identifier, "?2" is the firstSequenceNumber and "?3" is based on batchSize
         * parameters from {@link JdbcEventStorageEngine#readEventData(Connection, String, long, int)} and they should
         * <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder readEventDataForAggregateSqlStatement(
                Function<EventSchema, String> readEventDataForAggregateSqlStatement) {
            this.readEventDataForAggregateSqlStatement = readEventDataForAggregateSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#readSnapshotData}. Defaults to:
         * <p/>
         * {@code "SELECT [domainEventFields] FROM [snapshotTable] WHERE [aggregateIdentifierColumn] = ? ORDER BY
         * [sequenceNumberColumn] DESC" }
         * <p/>
         * <b>NOTE:</b> "?" is the identifier parameter from {@link JdbcEventStorageEngine#readSnapshotData}
         * and should <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder readSnapshotDataSqlStatement(Function<EventSchema, String> readSnapshotDataSqlStatement) {
            this.readSnapshotDataSqlStatement = readSnapshotDataSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken,
         * int)} when there is no gaps on the {@link GapAwareTrackingToken}. Defaults to:
         * <p/>
         * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE ([globalIndexColumn] > ?1 AND
         * [globalIndexColumn] <= ?2) ORDER BY [globalIndexColumn] ASC" }
         * <p/>
         * <b>NOTE:</b> "?1" is the globalIndex and "?2" is the batchSize parameters from {@link
         * JdbcEventStorageEngine#readEventData(Connection, TrackingToken, int)} and they should <b>always</b> be
         * present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder readEventDataWithoutGapsSqlStatement(
                Function<EventSchema, String> readEventDataWithoutGapsSqlStatement) {
            this.readEventDataWithoutGapsSqlStatement = readEventDataWithoutGapsSqlStatement;
            return this;
        }

        /**
         * Set the SqlStatement to be used on {@link JdbcEventStorageEngine#readEventData(Connection, TrackingToken,
         * int)} when there are gaps on the {@link GapAwareTrackingToken}. Defaults to:
         * <p/>
         * {@code "SELECT [trackedEventFields] FROM [domainEventTable] WHERE ([globalIndexColumn] > ?1 AND
         * [globalIndexColumn] <= ?2) OR [globalIndexColumn] IN (?3 .. ?n) ORDER BY [globalIndexColumn] ASC" }
         * <p/>
         * <b>NOTE:</b> "?1" is the globalIndex and "?2" is the batchSize parameters from {@link
         * JdbcEventStorageEngine#readEventData(Connection, TrackingToken, int)}. "?3 .. ?n" is taken from the {@link
         * GapAwareTrackingToken#getGaps()} and they should <b>always</b> be present for the SqlStatement to work.
         *
         * @return the modified Builder instance
         */
        public Builder readEventDataWithGapsSqlStatement(
                BiFunction<EventSchema, Integer, String> readEventDataWithGapsSqlStatement) {
            this.readEventDataWithGapsSqlStatement = readEventDataWithGapsSqlStatement;
            return this;
        }

        /**
         * Builds a new {@link EventSchema} from builder values.
         *
         * @return new EventSchema
         */
        public EventSchema build() {
            return new EventSchema(this);
        }
    }
}
