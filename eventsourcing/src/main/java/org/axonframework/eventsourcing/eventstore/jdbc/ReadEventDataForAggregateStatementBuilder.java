package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface ReadEventDataForAggregateStatementBuilder {

    PreparedStatement apply(Connection connection, String identifier, long firstSequenceNumber, int batchSize)
            throws SQLException;
}
