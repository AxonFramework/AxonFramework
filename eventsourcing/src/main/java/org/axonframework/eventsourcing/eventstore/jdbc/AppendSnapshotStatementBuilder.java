package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface AppendSnapshotStatementBuilder {

    PreparedStatement apply(Connection connection, DomainEventMessage<?> snapshot,
                            Serializer serializer) throws SQLException;
}
