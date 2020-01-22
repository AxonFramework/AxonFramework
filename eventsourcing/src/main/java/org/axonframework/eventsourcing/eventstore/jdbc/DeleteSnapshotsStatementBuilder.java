package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface DeleteSnapshotsStatementBuilder {

    PreparedStatement apply(Connection connection, String aggregateIdentifier, long sequenceNumber) throws SQLException;
}
