/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jdbc;

import org.axonframework.serializer.SerializedDomainEventData;
import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface describing the operations that the JDBC Event Store needs to do on a backing database. This abstraction
 * allows for different SQL dialects to be used in cases where the default doesn't suffice.
 *
 * @author Kristian Rosenvold
 * @author Allard Buijze
 * @since 2.2
 */
public interface EventSqlSchema {

    /**
     * Creates the PreparedStatement for loading the last snapshot event for an aggregate with given
     * <code>identifier</code> and of given <code>aggregateType</code>.
     *
     * @param connection    The connection to create the PreparedStatement for
     * @param identifier    The identifier of the aggregate to find the snapshot for
     * @param aggregateType The type identifier of the aggregate
     * @return a PreparedStatement with all parameters set
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_loadLastSnapshot(Connection connection, Object identifier, String aggregateType)
            throws SQLException;

    /**
     * Creates the PreparedStatement for inserting a DomainEvent in the Event Store, using given attributes.
     *
     * @param connection          The connection to create the PreparedStatement for
     * @param eventIdentifier     The unique identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate that generated the event
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The time at which the Event Message was generated
     * @param eventType           The type identifier of the serialized event
     * @param eventRevision       The revision of the serialized event
     * @param eventPayload        The serialized payload of the Event
     * @param eventMetaData       The serialized meta data of the event
     * @param aggregateType       The type identifier of the aggregate the event belongs to
     * @return a PreparedStatement with all parameters set
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_insertDomainEventEntry(Connection connection, String eventIdentifier,
                                                 String aggregateIdentifier, long sequenceNumber,
                                                 DateTime timestamp, String eventType, String eventRevision,
                                                 byte[] eventPayload,
                                                 byte[] eventMetaData,
                                                 String aggregateType) throws SQLException;

    /**
     * Creates the PreparedStatement for inserting a Snapshot Event in the Event Store, using given attributes.
     *
     * @param connection          The connection to create the PreparedStatement for
     * @param eventIdentifier     The unique identifier of the event
     * @param aggregateIdentifier The identifier of the aggregate that generated the event
     * @param sequenceNumber      The sequence number of the event
     * @param timestamp           The time at which the Event Message was generated
     * @param eventType           The type identifier of the serialized event
     * @param eventRevision       The revision of the serialized event
     * @param eventPayload        The serialized payload of the Event
     * @param eventMetaData       The serialized meta data of the event
     * @param aggregateType       The type identifier of the aggregate the event belongs to
     * @return a PreparedStatement with all parameters set
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_insertSnapshotEventEntry(Connection connection, String eventIdentifier,
                                                   String aggregateIdentifier, long sequenceNumber,
                                                   DateTime timestamp, String eventType, String eventRevision,
                                                   byte[] eventPayload,
                                                   byte[] eventMetaData,
                                                   String aggregateType) throws SQLException;

    /**
     * Creates a PreparedStatement that deletes all snapshots with a sequence identifier equal or lower to the given
     * <code>sequenceOfFirstSnapshotToPrune</code>, for an aggregate of given <code>type</code> and
     * <code>aggregateIdentifier</code>.
     *
     * @param connection                     The connection to create the PreparedStatement for
     * @param type                           The type identifier of the aggregate
     * @param aggregateIdentifier            The identifier of the aggregate
     * @param sequenceOfFirstSnapshotToPrune The sequence number of the most recent snapshot to prune
     * @return The PreparedStatement, ready to execute
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_pruneSnapshots(Connection connection, String type, Object aggregateIdentifier,
                                         long sequenceOfFirstSnapshotToPrune) throws SQLException;

    /**
     * Creates a PreparedStatement that returns the sequence numbers of snapshots for an aggregate of given
     * <code>type</code> and <code>aggregateIdentifier</code>.
     *
     * @param connection          The connection to create the PreparedStatement for
     * @param type                The type identifier of the aggregate
     * @param aggregateIdentifier The identifier of the aggregate
     * @return The PreparedStatement, ready to execute, returning a single column with longs.
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_findSnapshotSequenceNumbers(Connection connection, String type, Object aggregateIdentifier)
            throws SQLException;

    /**
     * Creates a PreparedStatement that fetches event data for an aggregate with given <code>type</code> and
     * <code>identifier</code>, starting at the given <code>firstSequenceNumber</code>.
     *
     * @param connection          The connection to create the PreparedStatement for
     * @param type                The type identifier of the aggregate
     * @param aggregateIdentifier The identifier of the aggregate
     * @param firstSequenceNumber The sequence number of the first event to return
     * @return a PreparedStatement that returns columns that can be converted using {@link
     * #createSerializedDomainEventData(java.sql.ResultSet)}
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_fetchFromSequenceNumber(Connection connection, String type, Object aggregateIdentifier,
                                                  long firstSequenceNumber) throws SQLException;

    /**
     * Creates a PreparedStatement that fetches all event messages matching the given <code>whereClause</code>. The
     * given <code>parameters</code> provide the parameters used in the where clause, in the order of declaration.
     *
     * @param connection  The connection to create the PreparedStatement for
     * @param whereClause The SQL snippet to use after the WHERE directive. May be <code>null</code> or an empty String
     *                    to indicate that no filter is to be applied
     * @param parameters  The parameters to set in the WHERE clause
     * @return a PreparedStatement that returns columns that can be converted using {@link
     * #createSerializedDomainEventData(java.sql.ResultSet)}
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_getFetchAll(Connection connection, String whereClause, Object[] parameters)
            throws SQLException;

    /**
     * Creates a PreparedStatement that allows for the creation of the table to store Snapshots.
     *
     * @param connection The connection to create the PreparedStatement for
     * @return The Prepared Statement, ready to be executed
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_createSnapshotEventEntryTable(Connection connection) throws SQLException;

    /**
     * Creates a PreparedStatement that allows for the creation of the table to store Event entries.
     *
     * @param connection The connection to create the PreparedStatement for
     * @return The Prepared Statement, ready to be executed
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    PreparedStatement sql_createDomainEventEntryTable(Connection connection) throws SQLException;

    /**
     * Reads the current entry of the ResultSet into a SerializedDomainEventData.
     * <p/>
     * Note: the implementation *must* not change the ResultSet's cursor position.
     *
     * @param resultSet The result set returned from executing one of the Prepared Statements declared on this
     *                  interface
     * @return a single SerializedDomainEventData instance
     *
     * @throws SQLException when an exception occurs while creating the prepared statement
     * @see org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData
     */
    SerializedDomainEventData createSerializedDomainEventData(ResultSet resultSet) throws SQLException;

    /**
     * Converts a {@link DateTime} to a data value suitable for the database scheme.
     *
     * @param input {@link DateTime} to convert
     * @return data representing the date time suitable for the current SQL scheme
     */
    Object sql_dateTime(DateTime input);
}
