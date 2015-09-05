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
package org.axonframework.eventstore.jdbc;

import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventData;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static java.lang.String.format;

/**
 * @param <T> The type used when storing serialized data
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.2
 */
@SuppressWarnings("JpaQueryApiInspection")
public class GenericEventSqlSchema<T> implements EventSqlSchema<T> {

    private static final DateTimeFormatter UTC_FORMATTER = ISODateTimeFormat.dateTime().withZoneUTC();

    private final Class<T> dataType;

    private boolean forceUtc = false;

    protected SchemaConfiguration schemaConfiguration;

    private final String sqlInsertEventEntry;
    private final String sqlLoadLastSnapshot;
    private final String sqlPruneSnapshots;
    private final String sqlFindSnapshotSequenceNumbers;
    private final String sqlFetchFromSequenceNumber;
    private final String sqlGetFetchAll;
    private final String sqlCreateSnapshotEventEntryTable;
    private final String sqlCreateDomainEventEntryTable;

    /**
     * Initialize a GenericEventSqlSchema using default settings.
     * <p/>
     * Serialized data is stored as byte arrays. Data is stored in a default SchemaConfiguration.
     */
    @SuppressWarnings("unchecked")
    public GenericEventSqlSchema() {
        this((Class<T>) byte[].class, new SchemaConfiguration());
    }

    /**
     * Initialize a GenericEventSqlSchema.
     * <p/>
     * Serialized data is stored using the given <code>dataType</code>. Data is stored in a default SchemaConfiguration.
     *
     * @param dataType The type to use when storing serialized data
     */
    public GenericEventSqlSchema(Class<T> dataType) {
        this(dataType, new SchemaConfiguration());
    }

    /**
     * Initialize a GenericEventSqlSchema.
     * <p/>
     * Serialized data is stored using the given <code>dataType</code>. Data is stored according to the given SchemaConfiguration.
     *
     * @param dataType
     * @param schemaConfiguration
     */
    public GenericEventSqlSchema(Class<T> dataType, SchemaConfiguration schemaConfiguration) {
        this.dataType = dataType;
        this.schemaConfiguration = schemaConfiguration;

        sqlInsertEventEntry = buildSqlInsertEventEntryQuery(schemaConfiguration);
        sqlLoadLastSnapshot = buildSqlLoadLastSnapshotQuery(schemaConfiguration);
        sqlPruneSnapshots = buildSqlPruneSnapshotsQuery(schemaConfiguration);
        sqlFindSnapshotSequenceNumbers = buildSqlFindSnapshotSequenceNumbersQuery(schemaConfiguration);
        sqlFetchFromSequenceNumber = buildSqlFetchFromSequenceNumberQuery(schemaConfiguration);
        sqlGetFetchAll = buildSqlFetchAllQuery(schemaConfiguration);
        sqlCreateSnapshotEventEntryTable = buildSqlCreateSnapshotEventEntryTable(schemaConfiguration);
        sqlCreateDomainEventEntryTable = buildSqlCreateDomainEventEntryTable(schemaConfiguration);
    }

    /**
     * Control if date time in the SQL scheme
     * should use UTC time zone or system local time zone.
     * <p/>
     * <p>
     * Default is to use system local time zone.
     * </p>
     * <p/>
     * <p>
     * You should not change this after going into production,
     * since it would affect the batching iterator when fetching events.
     * </p>
     *
     * @param forceUtc set true to force all date times to use UTC time zone
     */
    public void setForceUtc(boolean forceUtc) {
        this.forceUtc = forceUtc;
    }

    @Override
    public PreparedStatement sql_loadLastSnapshot(Connection connection, Object identifier, String aggregateType)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sqlLoadLastSnapshot);
        statement.setString(1, identifier.toString());
        statement.setString(2, aggregateType);
        return statement;
    }

    @Override
    public PreparedStatement sql_insertDomainEventEntry(Connection conn, String eventIdentifier,
                                                        String aggregateIdentifier, long sequenceNumber,
                                                        DateTime timestamp, String eventType, String eventRevision,
                                                        T eventPayload, T eventMetaData, String aggregateType)
            throws SQLException {
        return doInsertEventEntry(conn, eventIdentifier, aggregateIdentifier, sequenceNumber, timestamp,
                eventType, eventRevision, eventPayload,
                eventMetaData, aggregateType);
    }

    @Override
    public PreparedStatement sql_insertSnapshotEventEntry(Connection conn, String eventIdentifier,
                                                          String aggregateIdentifier, long sequenceNumber,
                                                          DateTime timestamp, String eventType, String eventRevision,
                                                          T eventPayload, T eventMetaData,
                                                          String aggregateType) throws SQLException {
        return doInsertEventEntry(conn, eventIdentifier, aggregateIdentifier, sequenceNumber, timestamp,
                eventType, eventRevision, eventPayload,
                eventMetaData, aggregateType);
    }

    /**
     * Creates a statement to insert an entry with given attributes in the given <code>tableName</code>. This method
     * is used by {@link #sql_insertDomainEventEntry(java.sql.Connection, String, String, long, org.joda.time.DateTime,
     * String, String, Object, Object, String)} and {@link #sql_insertSnapshotEventEntry(java.sql.Connection, String,
     * String, long, org.joda.time.DateTime, String, String, Object, Object, String)}, and provides an easier way to
     * change to types of columns used.
     *
     * @param connection          The connection to create the statement for
     * @param eventIdentifier     The unique identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate that generated the event
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The time at which the Event Message was generated
     * @param eventType           The type identifier of the serialized event
     * @param eventRevision       The revision of the serialized event
     * @param eventPayload        The serialized payload of the Event
     * @param eventMetaData       The serialized meta data of the event
     * @param aggregateType       The type identifier of the aggregate the event belongs to
     * @return a prepared statement that allows inserting a domain event entry when executed
     * @throws SQLException when an exception occurs creating the PreparedStatement
     */
    protected PreparedStatement doInsertEventEntry(Connection connection, String eventIdentifier,
                                                   String aggregateIdentifier,
                                                   long sequenceNumber, DateTime timestamp, String eventType,
                                                   String eventRevision,
                                                   T eventPayload, T eventMetaData, String aggregateType)
            throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sqlInsertEventEntry);
        preparedStatement.setString(1, eventIdentifier);
        preparedStatement.setString(2, aggregateType);
        preparedStatement.setString(3, aggregateIdentifier);
        preparedStatement.setLong(4, sequenceNumber);
        preparedStatement.setString(5, sql_dateTime(timestamp));
        preparedStatement.setString(6, eventType);
        preparedStatement.setString(7, eventRevision);
        preparedStatement.setObject(8, eventPayload);
        preparedStatement.setObject(9, eventMetaData);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_pruneSnapshots(Connection connection, String type, Object aggregateIdentifier,
                                                long sequenceOfFirstSnapshotToPrune) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sqlPruneSnapshots);
        preparedStatement.setString(1, type);
        preparedStatement.setString(2, aggregateIdentifier.toString());
        preparedStatement.setLong(3, sequenceOfFirstSnapshotToPrune);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_findSnapshotSequenceNumbers(Connection connection, String type,
                                                             Object aggregateIdentifier) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sqlFindSnapshotSequenceNumbers);
        preparedStatement.setString(1, type);
        preparedStatement.setString(2, aggregateIdentifier.toString());
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_fetchFromSequenceNumber(Connection connection, String type, Object aggregateIdentifier,
                                                         long firstSequenceNumber) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sqlFetchFromSequenceNumber);
        preparedStatement.setString(1, aggregateIdentifier.toString());
        preparedStatement.setString(2, type);
        preparedStatement.setLong(3, firstSequenceNumber);
        return preparedStatement;
    }

    @Override
    public PreparedStatement sql_getFetchAll(Connection connection, String whereClause,
                                             Object[] params) throws SQLException {
        String query = format(sqlGetFetchAll, whereClause);
        PreparedStatement preparedStatement = connection.prepareStatement(query);
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param instanceof DateTime) {
                param = sql_dateTime((DateTime) param);
            }

            if (param instanceof byte[]) {
                preparedStatement.setBytes(i + 1, (byte[]) param);
            } else {
                preparedStatement.setObject(i + 1, param);
            }
        }
        return preparedStatement;
    }

    /**
     * Reads a timestamp from the given <code>resultSet</code> at given <code>columnIndex</code>. The resultSet is
     * positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet   The resultSet containing the stored data
     * @param columnIndex The column containing the timestamp
     * @return an object describing the timestamp
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    protected Object readTimeStamp(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /**
     * Reads a serialized object from the given <code>resultSet</code> at given <code>columnIndex</code>. The resultSet
     * is positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet   The resultSet containing the stored data
     * @param columnIndex The column containing the timestamp
     * @return an object describing the serialized data
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    @SuppressWarnings("unchecked")
    protected T readPayload(ResultSet resultSet, int columnIndex) throws SQLException {
        if (byte[].class.equals(dataType)) {
            return (T) resultSet.getBytes(columnIndex);
        }
        return (T) resultSet.getObject(columnIndex);
    }

    @Override
    public PreparedStatement sql_createSnapshotEventEntryTable(Connection connection) throws SQLException {
        return connection.prepareStatement(sqlCreateSnapshotEventEntryTable);
    }

    @Override
    public PreparedStatement sql_createDomainEventEntryTable(Connection connection) throws SQLException {
        return connection.prepareStatement(sqlCreateDomainEventEntryTable);
    }

    @Override
    public SerializedDomainEventData<T> createSerializedDomainEventData(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedDomainEventData<T>(resultSet.getString(1), resultSet.getString(2),
                resultSet.getLong(3), readTimeStamp(resultSet, 4),
                resultSet.getString(5), resultSet.getString(6),
                readPayload(resultSet, 7),
                readPayload(resultSet, 8));
    }

    @Override
    public String sql_dateTime(DateTime input) {
        if (forceUtc) {
            return input.toString(UTC_FORMATTER);
        } else {
            return input.toString();
        }
    }

    @Override
    public Class<T> getDataType() {
        return dataType;
    }

    @Override
    public SchemaConfiguration getSchemaConfiguration() {
        return schemaConfiguration;
    }

    /**
     * INSERT INTO
     *    DomainEventEntry (
     *        eventIdentifier, type, aggregateIdentifier,
     *        sequenceNumber, timeStamp, payloadType,
     *        payloadRevision, payload, metaData
     *    ) VALUES (
     *        ?,?,?,?,?,?,?,?,?
     *    )
     */
    public String buildSqlInsertEventEntryQuery(SchemaConfiguration sc) {
        return format("INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s, %s) VALUES (?,?,?,?,?,?,?,?,?)",
                sc.domainEventEntryTable(),
                sc.eventIdentifierColumn(),
                sc.typeColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.timeStampColumn(),
                sc.payloadTypeColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadColumn(),
                sc.metaDataColumn()
        );
    }

    /**
     * SELECT
     *     eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, payloadRevision, payload, metaData
     * FROM
     *     SnapshotEventEntry
     * WHERE
     *     aggregateIdentifier = ?
     *     AND type = ?
     * ORDER BY
     *      sequenceNumber DESC
     */
    public String buildSqlLoadLastSnapshotQuery(SchemaConfiguration sc) {
        return format("SELECT %s, %s, %s, %s, %s, %s, %s, %s FROM %s WHERE %s = ? AND %s = ? ORDER BY %s DESC",
                sc.eventIdentifierColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.timeStampColumn(),
                sc.payloadTypeColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadColumn(),
                sc.metaDataColumn(),
                sc.snapshotEntryTable(),
                sc.aggregateIdentifierColumn(),
                sc.typeColumn(),
                sc.sequenceNumberColumn()
        );
    }

    /**
     * DELETE FROM
     *     SnapshotEventEntry
     * WHERE
     *     type = ?
     *     AND aggregateIdentifier = ?
     *     AND sequenceNumber <= ?
     */
    public String buildSqlPruneSnapshotsQuery(SchemaConfiguration sc) {
        return format("DELETE FROM %s WHERE %s = ? AND %s = ? AND %s <= ?",
                sc.snapshotEntryTable(),
                sc.typeColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn()
        );
    }

    /**
     * SELECT
     *     sequenceNumber
     * FROM
     *     SnapshotEventEntry
     * WHERE
     *     type = ?
     *     AND aggregateIdentifier = ?
     * ORDER BY
     *     sequenceNumber DESC
     */
    public String buildSqlFindSnapshotSequenceNumbersQuery(SchemaConfiguration sc) {
        return format("SELECT %s FROM %s WHERE %s = ? AND %s = ? ORDER BY %s DESC",
                sc.sequenceNumberColumn(),
                sc.snapshotEntryTable(),
                sc.typeColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn()
        );
    }

    /**
     *  SELECT
     *       eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, payloadRevision, payload, metaData
     *  FROM
     *       DomainEventEntry
     *  WHERE
     *     aggregateIdentifier = ?
     *     AND type = ?
     *     AND sequenceNumber >= ?
     *  ORDER BY
     *     sequenceNumber ASC
     */
    public String buildSqlFetchFromSequenceNumberQuery(SchemaConfiguration sc) {
        return format("SELECT %s, %s, %s, %s, %s, %s, %s, %s FROM %s WHERE %s = ? AND %s = ? AND %s >= ? ORDER BY %s ASC",
                sc.eventIdentifierColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.timeStampColumn(),
                sc.payloadTypeColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadColumn(),
                sc.metaDataColumn(),
                sc.domainEventEntryTable(),
                sc.aggregateIdentifierColumn(),
                sc.typeColumn(),
                sc.sequenceNumberColumn(),
                sc.sequenceNumberColumn()
        );
    }

    /**
     * SELECT
     *     eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, payloadRevision, payload, metaData
     *  FROM
     *      DomainEventEntry e
     *  WHERE
     *      {whereClause}
     *  ORDER BY
     *      e.timeStamp ASC,
     *      e.sequenceNumber ASC,
     *      e.aggregateIdentifier ASC
     */
    public String buildSqlFetchAllQuery(SchemaConfiguration sc) {
        return format("select %s, %s, %s, %s, %s, %s, %s, %s from %s e %%s ORDER BY e.%s ASC, e.%s ASC, e.%s ASC ",
                sc.eventIdentifierColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.timeStampColumn(),
                sc.payloadTypeColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadColumn(),
                sc.metaDataColumn(),
                sc.domainEventEntryTable(),
                sc.timeStampColumn(),
                sc.sequenceNumberColumn(),
                sc.aggregateIdentifierColumn()
        );
    }

    /**
     * CREATE TABLE
     *     SnapshotEventEntry (
     *        aggregateIdentifier varchar(255) not null,
     *        sequenceNumber bigint not null,
     *        type varchar(255) not null,
     *        eventIdentifier varchar(255) not null,
     *        metaData blob,
     *        payload blob not null,
     *        payloadRevision varchar(255),
     *        payloadType varchar(255) not null,
     *        timeStamp varchar(255) not null,
     *        primary key (aggregateIdentifier, sequenceNumber, type)
     *     )
     */
    public String buildSqlCreateSnapshotEventEntryTable(SchemaConfiguration sc) {
        return format("create table %s (%s varchar(255) not null, %s bigint not null, %s varchar(255) not null, " +
                        "%s varchar(255) not null, %s blob, %s blob not null, %s varchar(255), %s varchar(255) not null, " +
                        "%s varchar(255) not null, primary key (%s, %s, %s) )",
                sc.snapshotEntryTable(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.typeColumn(),
                sc.eventIdentifierColumn(),
                sc.metaDataColumn(),
                sc.payloadColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadTypeColumn(),
                sc.timeStampColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.typeColumn()
        );
    }

    /**
     * CREATE TABLE
     *     DomainEventEntry (
     *        aggregateIdentifier varchar(255) not null,
     *        sequenceNumber bigint not null,
     *        type varchar(255) not null,
     *        eventIdentifier varchar(255) not null,
     *        metaData blob,
     *        payload blob not null,
     *        payloadRevision varchar(255),
     *        payloadType varchar(255) not null,
     *        timeStamp varchar(255) not null,
     *        primary key (aggregateIdentifier, sequenceNumber, type)
     *     )
     */
    public String buildSqlCreateDomainEventEntryTable(SchemaConfiguration sc) {
        return format("create table %s (%s varchar(255) not null, %s bigint not null, %s varchar(255) not null, " +
                        "%s varchar(255) not null, %s blob, %s blob not null, %s varchar(255), %s varchar(255) not null, " +
                        "%s varchar(255) not null, primary key (%s, %s, %s) )",
                sc.domainEventEntryTable(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.typeColumn(),
                sc.eventIdentifierColumn(),
                sc.metaDataColumn(),
                sc.payloadColumn(),
                sc.payloadRevisionColumn(),
                sc.payloadTypeColumn(),
                sc.timeStampColumn(),
                sc.aggregateIdentifierColumn(),
                sc.sequenceNumberColumn(),
                sc.typeColumn()
        );
    }

}
