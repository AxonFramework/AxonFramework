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
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.DefaultEventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchemaConfiguration;
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
public class LegacyEventSchema extends DefaultEventSchema {

    public LegacyEventSchema() {
        super();
    }

    public LegacyEventSchema(Class<?> dataType) {
        super(dataType);
    }

    public LegacyEventSchema(EventSchemaConfiguration config, Class<?> dataType) {
        super(config, dataType);
    }

    @Override
    public PreparedStatement readEventData(Connection connection, TrackingToken lastToken) throws SQLException {
        Assert.isTrue(lastToken == null || lastToken instanceof LegacyTrackingToken,
                      String.format("Token %s is of the wrong type", lastToken));
        String selectFrom = "SELECT " + trackedEventFields() + " FROM " + config().domainEventTable();
        String orderBy =
                " ORDER BY " + config().timestampColumn() + " ASC, " + config().sequenceNumberColumn() + " ASC, " +
                        config().aggregateIdentifierColumn() + " ASC";
        if (lastToken == null) {
            return connection.prepareStatement(selectFrom + orderBy);
        } else {
            LegacyTrackingToken lastItem = (LegacyTrackingToken) lastToken;
            String where = " WHERE ((" + config().timestampColumn() + " > ?) " +
                    "OR (" + config().timestampColumn() + " = ? AND " + config().sequenceNumberColumn() + " > ?) " +
                    "OR (" + config().timestampColumn() + " = ? AND " + config().sequenceNumberColumn() + " = ? " +
                    "AND " + config().aggregateIdentifierColumn() + " > ?))";
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
        return new GenericLegacyDomainEventEntry<>(resultSet.getString(config().typeColumn()),
                                                   resultSet.getString(config().aggregateIdentifierColumn()),
                                                   resultSet.getLong(config().sequenceNumberColumn()),
                                                   resultSet.getString(config().eventIdentifierColumn()),
                                                   readTimeStamp(resultSet, config().timestampColumn()),
                                                   resultSet.getString(config().payloadTypeColumn()),
                                                   resultSet.getString(config().payloadRevisionColumn()),
                                                   readPayload(resultSet, config().payloadColumn()),
                                                   readPayload(resultSet, config().metaDataColumn()));
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
