package org.axonframework.eventsourcing.eventstore.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@FunctionalInterface
public interface ReadEventDataWithGapsStatementBuilder {

    PreparedStatement apply(Connection connection, long globalIndex, int batchSize, List<Long> gaps)
            throws SQLException;
}
