package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface FetchTrackedEventsStatementBuilder {

    PreparedStatement apply(Connection connection, long index) throws SQLException;
}
