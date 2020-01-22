package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface CreateHeadTokenStatementBuilder {

    PreparedStatement apply(Connection connection) throws SQLException;
}
