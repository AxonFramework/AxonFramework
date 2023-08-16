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

import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract which defines how to build a PreparedStatement for use on {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken,
 * int)}
 *
 * @author Lucas Campos
 * @since 4.3
 */
@FunctionalInterface
public interface ReadEventDataWithGapsStatementBuilder {

    /**
     * Creates a statement to be used at {@link JdbcEventStorageEngine#fetchTrackedEvents(TrackingToken, int)}
     *
     * @param connection  The connection to the database.
     * @param schema      The EventSchema to be used
     * @param globalIndex The index taken from the tracking token.
     * @param batchSize   The number of items to include in the batch
     * @param gaps        The Set of gaps taken from the tracking token.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     * @see JdbcEventStorageEngine#readEventData(Connection, TrackingToken, int)
     */
    PreparedStatement build(Connection connection, EventSchema schema, long globalIndex, int batchSize, List<Long> gaps)
            throws SQLException;
}
