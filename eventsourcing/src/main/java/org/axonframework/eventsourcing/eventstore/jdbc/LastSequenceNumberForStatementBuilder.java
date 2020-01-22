package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface LastSequenceNumberForStatementBuilder {

    PreparedStatement apply(Connection connection, String aggregateIdentifier) throws SQLException;
}
