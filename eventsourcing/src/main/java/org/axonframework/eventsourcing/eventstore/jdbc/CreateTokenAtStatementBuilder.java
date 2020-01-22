package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

@FunctionalInterface
public interface CreateTokenAtStatementBuilder {

    PreparedStatement apply(Connection connection, Instant datetime) throws SQLException;
}
