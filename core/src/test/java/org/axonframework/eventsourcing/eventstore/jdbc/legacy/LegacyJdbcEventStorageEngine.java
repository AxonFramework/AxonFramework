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

package org.axonframework.eventsourcing.eventstore.jdbc.legacy;

import org.axonframework.common.Assert;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.legacy.GenericLegacyDomainEventEntry;
import org.axonframework.eventsourcing.eventstore.legacy.LegacyTrackingToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;

/**
 * @author Rene de Waele
 */
public class LegacyJdbcEventStorageEngine extends JdbcEventStorageEngine {

    private static final long DEFAULT_GAP_DETECTION_INTERVAL_MILLIS = 10000L;

    private final long gapDetectionInterval;

    public LegacyJdbcEventStorageEngine(ConnectionProvider connectionProvider, TransactionManager transactionManager) {
        super(connectionProvider, transactionManager);
        this.gapDetectionInterval = DEFAULT_GAP_DETECTION_INTERVAL_MILLIS;
    }

    public LegacyJdbcEventStorageEngine(ConnectionProvider connectionProvider, TransactionManager transactionManager,
                                        EventSchema eventSchema, Class<?> dataType, long gapDetectionInterval) {
        super(connectionProvider, transactionManager, eventSchema, dataType);
        this.gapDetectionInterval = gapDetectionInterval;
    }

    @Override
    protected TrackingToken getTokenForGapDetection(TrackingToken token) {
        if (token == null) {
            return null;
        }
        Assert.isTrue(token instanceof LegacyTrackingToken, String.format("Token %s is of the wrong type", token));
        LegacyTrackingToken legacyToken = (LegacyTrackingToken) token;
        return new LegacyTrackingToken(legacyToken.getTimestamp(),
                                       legacyToken.getAggregateIdentifier(), legacyToken.getSequenceNumber());
    }

    @Override
    public PreparedStatement readEventData(Connection connection, TrackingToken lastToken) throws SQLException {
        Assert.isTrue(lastToken == null || lastToken instanceof LegacyTrackingToken,
                      String.format("Token [%s] is of the wrong type", lastToken));
        String selectFrom = "SELECT " + trackedEventFields() + " FROM " + schema().domainEventTable();
        String orderBy =
                " ORDER BY " + schema().timestampColumn() + " ASC, " + schema().sequenceNumberColumn() + " ASC, " +
                        schema().aggregateIdentifierColumn() + " ASC";
        if (lastToken == null) {
            return connection.prepareStatement(selectFrom + orderBy);
        } else {
            LegacyTrackingToken lastItem = (LegacyTrackingToken) lastToken;
            String where = " WHERE ((" + schema().timestampColumn() + " > ?) " +
                    "OR (" + schema().timestampColumn() + " = ? AND " + schema().sequenceNumberColumn() + " > ?) " +
                    "OR (" + schema().timestampColumn() + " = ? AND " + schema().sequenceNumberColumn() + " = ? " +
                    "AND " + schema().aggregateIdentifierColumn() + " > ?))";
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
    public TrackedEventData<?> getTrackedEventData(ResultSet resultSet) throws SQLException {
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
    public DomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException {
        return (DomainEventData<?>) getTrackedEventData(resultSet);
    }

    @Override
    protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    protected void writeTimestamp(PreparedStatement preparedStatement, int position,
                                  TemporalAccessor input) throws SQLException {
        preparedStatement.setString(position, Instant.from(input).toString());
    }

    @Override
    protected String trackedEventFields() {
        return domainEventFields();
    }
}
