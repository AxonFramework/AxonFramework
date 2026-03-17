/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventsourcing.eventstore.jdbc.statements;

import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.messaging.eventsourcing.eventstore.jdbc.LegacyJdbcEventStorageEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.SortedSet;

/**
 * Contract which defines how to build a PreparedStatement for use on
 * {@link LegacyJdbcEventStorageEngine#cleanGaps(TrackingToken)}
 *
 * @author Lucas Campos
 * @since 4.3
 */
@FunctionalInterface
public interface CleanGapsStatementBuilder {

    /**
     * Creates a statement to be used at {@link LegacyJdbcEventStorageEngine#cleanGaps(TrackingToken)}
     *
     * @param connection The connection to the database.
     * @param schema     The EventSchema to be used
     * @param gaps       The Set of gaps taken from the tracking token.
     * @return The newly created {@link PreparedStatement}.
     * @throws SQLException when an exception occurs while creating the prepared statement.
     */
    PreparedStatement build(Connection connection, EventSchema schema, SortedSet<Long> gaps) throws SQLException;
}
