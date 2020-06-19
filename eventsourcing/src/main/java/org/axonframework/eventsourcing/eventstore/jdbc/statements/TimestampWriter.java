package org.axonframework.eventsourcing.eventstore.jdbc.statements;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

@FunctionalInterface
public interface TimestampWriter {
    void writeTimestamp(PreparedStatement preparedStatement, int position, Instant timestamp) throws SQLException;
}
