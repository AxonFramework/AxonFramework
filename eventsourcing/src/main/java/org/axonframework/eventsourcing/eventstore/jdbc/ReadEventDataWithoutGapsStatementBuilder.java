package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface ReadEventDataWithoutGapsStatementBuilder {

    PreparedStatement apply(Connection connection, long globalIndex, int batchSize) throws SQLException;
}
