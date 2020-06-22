package org.axonframework.eventsourcing.eventstore.jdbc.statements;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;

/**
 * Writer interface for writing a formatted timestamp to a {@link PreparedStatement} during updates of the database.
 * Used in {@link AppendEventsStatementBuilder} and {@link AppendSnapshotStatementBuilder}.
 * Contract which defines how to build a PreparedStatement for use on {@link JdbcEventStorageEngine#cleanGaps(TrackingToken)}
 *
 * @author Trond Marius Øvstetun
 * @since 4.4
 */
@FunctionalInterface
public interface TimestampWriter {
    void writeTimestamp(PreparedStatement preparedStatement, int position, Instant timestamp) throws SQLException;
}
