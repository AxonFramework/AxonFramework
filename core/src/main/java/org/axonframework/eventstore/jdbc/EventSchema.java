/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jdbc;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventstore.SerializedDomainEventData;
import org.axonframework.eventstore.SerializedTrackedEventData;
import org.axonframework.eventstore.TrackingToken;
import org.axonframework.serializer.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Rene de Waele
 */
public interface EventSchema {

    PreparedStatement appendEvent(Connection connection, DomainEventMessage<?> event, Serializer serializer) throws SQLException;

    PreparedStatement deleteSnapshots(Connection connection, String aggregateIdentifier) throws SQLException;

    PreparedStatement storeSnapshot(Connection connection, DomainEventMessage<?> snapshot, Serializer serializer) throws SQLException;

    PreparedStatement readEventData(Connection connection, String identifier, long firstSequenceNumber) throws SQLException;

    PreparedStatement readEventData(Connection connection, TrackingToken lastToken) throws SQLException;

    PreparedStatement readSnapshotData(Connection connection, String identifier) throws SQLException;

    SerializedTrackedEventData<?> getTrackedEventData(ResultSet resultSet) throws SQLException;

    SerializedDomainEventData<?> getDomainEventData(ResultSet resultSet) throws SQLException;

    EventSchemaConfiguration schemaConfiguration();

}
