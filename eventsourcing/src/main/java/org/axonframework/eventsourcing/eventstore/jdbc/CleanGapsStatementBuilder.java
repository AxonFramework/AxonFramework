package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.SortedSet;

@FunctionalInterface
public interface CleanGapsStatementBuilder {

    PreparedStatement apply(Connection connection, SortedSet<Long> gaps) throws SQLException;
}
