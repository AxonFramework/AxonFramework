package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface ReadSnapshotDataStatementBuilder {

    PreparedStatement apply(Connection connection, String identifier) throws SQLException;
}
