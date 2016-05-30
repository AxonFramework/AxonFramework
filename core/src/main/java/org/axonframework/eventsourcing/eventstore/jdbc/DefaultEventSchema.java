/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.common.Assert;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;

/**
 * @author Rene de Waele
 */
public class DefaultEventSchema implements EventSchema {

    private final EventSchemaConfiguration config;
    private final Class<?> dataType;

    public DefaultEventSchema() {
        this(byte[].class);
    }

    public DefaultEventSchema(Class<?> dataType) {
        this(new EventSchemaConfiguration(), dataType);
    }

    public DefaultEventSchema(EventSchemaConfiguration config, Class<?> dataType) {
        this.config = config;
        this.dataType = dataType;
    }

    @Override
    public PreparedStatement appendEvent(Connection connection, DomainEventMessage<?> event,
                                         Serializer serializer) throws SQLException {
        return insertEvent(connection, config.domainEventTable(), event, serializer);
    }

    @Override
    public PreparedStatement appendSnapshot(Connection connection, DomainEventMessage<?> snapshot,
                                            Serializer serializer) throws SQLException {
        return insertEvent(connection, config.snapshotTable(), snapshot, serializer);
    }

    @SuppressWarnings("SqlInsertValues")
    protected PreparedStatement insertEvent(Connection connection, String table, DomainEventMessage<?> event,
                                            Serializer serializer) throws SQLException {
        SerializedObject<?> payload = serializer.serialize(event.getPayload(), dataType);
        SerializedObject<?> metaData = serializer.serialize(event.getMetaData(), dataType);
        final String sql = "INSERT INTO " + table + " (" +
                String.join(", ", config.eventIdentifierColumn(), config.aggregateIdentifierColumn(),
                            config.sequenceNumberColumn(), config.typeColumn(), config.timestampColumn(),
                            config.payloadTypeColumn(), config.payloadRevisionColumn(), config.payloadColumn(),
                            config.metaDataColumn()) + ") VALUES (?,?,?,?,?,?,?,?,?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql); // NOSONAR
        preparedStatement.setString(1, event.getIdentifier());
        preparedStatement.setString(2, event.getAggregateIdentifier());
        preparedStatement.setLong(3, event.getSequenceNumber());
        preparedStatement.setString(4, event.getType());
        writeTimestamp(preparedStatement, 5, event.getTimestamp());
        preparedStatement.setString(6, payload.getType().getName());
        preparedStatement.setString(7, payload.getType().getRevision());
        preparedStatement.setObject(8, payload.getData());
        preparedStatement.setObject(9, metaData.getData());
        return preparedStatement;
    }

    @Override
    public PreparedStatement deleteSnapshots(Connection connection, String aggregateIdentifier) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(
                "DELETE FROM " + config.snapshotTable() + " WHERE " + config.aggregateIdentifierColumn() + " = ?");
        preparedStatement.setString(1, aggregateIdentifier);
        return preparedStatement;
    }

    @Override
    public PreparedStatement readEventData(Connection connection, String identifier,
                                           long firstSequenceNumber) throws SQLException {
        final String sql = "SELECT " + trackedEventFields() + " FROM " + config.domainEventTable() +
                " WHERE " + config.aggregateIdentifierColumn() + " = ? AND " + config.sequenceNumberColumn() +
                " >= ? ORDER BY " + config.sequenceNumberColumn() + " ASC";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, identifier);
        preparedStatement.setLong(2, firstSequenceNumber);
        return preparedStatement;
    }

    @Override
    public PreparedStatement readEventData(Connection connection, TrackingToken lastToken) throws SQLException {
        Assert.isTrue(lastToken == null || lastToken instanceof GlobalIndexTrackingToken,
                      String.format("Token %s is of the wrong type", lastToken));
        final String sql = "SELECT " + trackedEventFields() + " FROM " + config.domainEventTable() +
                " WHERE " + config.globalIndexColumn() + " > ? ORDER BY " + config.globalIndexColumn() + " ASC";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setLong(1, lastToken == null ? -1 : ((GlobalIndexTrackingToken) lastToken).getGlobalIndex());
        return preparedStatement;
    }

    @Override
    public PreparedStatement readSnapshotData(Connection connection, String identifier) throws SQLException {
        final String s = "SELECT " + domainEventFields() + " FROM " + config.snapshotTable() + " WHERE " +
                config.aggregateIdentifierColumn() + " = ? ORDER BY " + config.sequenceNumberColumn() + " DESC";
        PreparedStatement statement = connection.prepareStatement(s);
        statement.setString(1, identifier);
        return statement;
    }

    @Override
    public TrackedEventData<?> getTrackedEventData(ResultSet resultSet) throws SQLException {
        return new GenericTrackedDomainEventEntry<>(resultSet.getLong(config.globalIndexColumn()),
                                                    resultSet.getString(config.typeColumn()),
                                                    resultSet.getString(config.aggregateIdentifierColumn()),
                                                    resultSet.getLong(config.sequenceNumberColumn()),
                                                    resultSet.getString(config.eventIdentifierColumn()),
                                                    readTimeStamp(resultSet, config.timestampColumn()),
                                                    resultSet.getString(config.payloadTypeColumn()),
                                                    resultSet.getString(config.payloadRevisionColumn()),
                                                    readPayload(resultSet, config.payloadColumn()),
                                                    readPayload(resultSet, config.metaDataColumn()));
    }

    @Override
    public DomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException {
        return new GenericDomainEventEntry<>(resultSet.getString(config.typeColumn()),
                                             resultSet.getString(config.aggregateIdentifierColumn()),
                                             resultSet.getLong(config.sequenceNumberColumn()),
                                             resultSet.getString(config.eventIdentifierColumn()),
                                             readTimeStamp(resultSet, config.timestampColumn()),
                                             resultSet.getString(config.payloadTypeColumn()),
                                             resultSet.getString(config.payloadRevisionColumn()),
                                             readPayload(resultSet, config.payloadColumn()),
                                             readPayload(resultSet, config.metaDataColumn()));
    }

    @Override
    public EventSchemaConfiguration schemaConfiguration() {
        return config;
    }

    /**
     * Reads a timestamp from the given <code>resultSet</code> at given <code>columnIndex</code>. The resultSet is
     * positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet  The resultSet containing the stored data
     * @param columnName The name of the column containing the timestamp
     * @return an object describing the timestamp
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    /**
     * Write a timestamp from a {@link TemporalAccessor} to a data value suitable for the database scheme.
     *
     * @param input {@link TemporalAccessor} to convert
     */
    protected void writeTimestamp(PreparedStatement preparedStatement, int position,
                                  TemporalAccessor input) throws SQLException {
        preparedStatement.setString(position, Instant.from(input).toString());
    }

    /**
     * Reads a serialized object from the given <code>resultSet</code> at given <code>columnIndex</code>. The resultSet
     * is positioned in the row that contains the data. This method must not change the row in the result set.
     *
     * @param resultSet  The resultSet containing the stored data
     * @param columnName The name of the column containing the payload
     * @return an object describing the serialized data
     * @throws SQLException when an exception occurs reading from the resultSet.
     */
    @SuppressWarnings("unchecked")
    protected <T> T readPayload(ResultSet resultSet, String columnName) throws SQLException {
        if (byte[].class.equals(dataType)) {
            return (T) resultSet.getBytes(columnName);
        }
        return (T) resultSet.getObject(columnName);
    }

    protected String domainEventFields() {
        return String.join(", ", config.eventIdentifierColumn(), config.timestampColumn(), config.payloadTypeColumn(),
                           config.payloadRevisionColumn(), config.payloadColumn(), config.metaDataColumn(),
                           config.typeColumn(), config.aggregateIdentifierColumn(), config.sequenceNumberColumn());
    }

    protected String trackedEventFields() {
        return config.globalIndexColumn() + ", " + domainEventFields();
    }

    protected EventSchemaConfiguration config() {
        return config;
    }

    protected Class<?> dataType() {
        return dataType;
    }
}
