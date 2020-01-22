package org.axonframework.eventsourcing.eventstore.jdbc;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@FunctionalInterface
public interface AppendEventsStatementBuilder {

    PreparedStatement apply(Connection connection, List<? extends EventMessage<?>> events, Serializer serializer)
            throws SQLException;
}
