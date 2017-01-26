/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.tokenstore.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.eventhandling.tokenstore.GenericTokenEntry;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;

import static java.lang.String.format;
import static org.axonframework.common.jdbc.JdbcUtils.*;

/**
 * Implementation of a token store that uses JDBC to save and load tokens. Before using this store make sure the
 * database contains a table named {@link TokenSchema#tokenTable()} in which to store the tokens.
 *
 * @author Rene de Waele
 */
public class JdbcTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTokenStore.class);

    private final ConnectionProvider connectionProvider;
    private final Serializer serializer;
    private final TokenSchema schema;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final Class<?> contentType;

    /**
     * Initialize the JpaTokenStore with given resources. The given {@code claimTimeout} is used to 'steal' any claim
     * that has not been updated since that amount of time. The token is serialized to a byte array.
     *
     * @param connectionProvider The provider of connections to the underlying database
     * @param serializer         The serializer to serialize tokens with
     */
    public JdbcTokenStore(ConnectionProvider connectionProvider, Serializer serializer) {
        this(connectionProvider, serializer, new TokenSchema(), Duration.ofSeconds(10),
             ManagementFactory.getRuntimeMXBean().getName(), byte[].class);
    }

    /**
     * Initialize the JpaTokenStore with given resources. The given {@code claimTimeout} is used to 'steal' any claim
     * that has not been updated since that amount of time.
     *
     * @param connectionProvider The provider of connections to the underlying database
     * @param serializer         The serializer to serialize tokens with
     * @param schema             The schema that describes a Jdbc token entry
     * @param claimTimeout       The timeout after which this process will force a claim
     * @param nodeId             The identifier to identify ownership of the tokens
     * @param contentType        The data type of the serialized token
     */
    public JdbcTokenStore(ConnectionProvider connectionProvider, Serializer serializer, TokenSchema schema,
                          TemporalAmount claimTimeout, String nodeId, Class<?> contentType) {
        this.connectionProvider = connectionProvider;
        this.serializer = serializer;
        this.schema = schema;
        this.claimTimeout = claimTimeout;
        this.nodeId = nodeId;
        this.contentType = contentType;
    }

    /**
     * Performs the DDL queries to create the schema necessary for this token store implementation.
     *
     * @param schemaFactory factory of the token entry schema
     * @throws EventStoreException when an error occurs executing SQL statements
     */
    public void createSchema(TokenTableFactory schemaFactory) {
        Connection c = getConnection();
        try {
            executeUpdates(c, e -> {
                throw new JdbcException("Failed to create token tables", e);
            }, connection -> schemaFactory.createTable(connection, schema));
        } finally {
            closeQuietly(c);
        }
    }

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            executeQuery(connection,
                         c -> selectForUpdate(c, processorName, segment),
                         resultSet -> {
                             insertOrUpdateToken(resultSet, token, processorName, segment);
                             if (!connection.getAutoCommit()) {
                                 connection.commit();
                             }
                             return null;
                         },
                         e -> new JdbcException(format("Could not store token [%s] for processor [%s] and segment [%d]",
                                                       token, processorName, segment), e));
        } finally {
            closeQuietly(connection);
        }
    }

    @Override
    public TrackingToken fetchToken(String processorName, int segment) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            return executeQuery(connection, c -> selectForUpdate(c, processorName, segment), resultSet -> {
                TrackingToken result = loadOrInsertToken(resultSet, processorName, segment);
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
                return result;
            }, e -> new JdbcException(
                    format("Could not load token for processor [%s] and segment [%d]", processorName, segment), e));
        } finally {
            closeQuietly(connection);
        }
    }

    @Override
    public void releaseClaim(String processorName, int segment) {
        Connection connection = getConnection();
        try {
            int[] result = executeUpdates(connection, e -> {
                                              throw new JdbcException(
                                                      format("Could not load token for processor [%s] and segment " + "[%d]",
                                                             processorName, segment), e);
                                          },
                                          c -> releaseClaim(c, processorName, segment));
            try {
                if (!connection.isClosed() && !connection.getAutoCommit()) {
                    connection.commit();
                }
            } catch (SQLException e) {
                // ignore
            }
            if (result[0] < 1) {
                logger.warn("Releasing claim of token {}/{} failed. It was owned by another node.", processorName, segment);
            }
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Returns a {@link PreparedStatement} to select a token entry from the underlying storage. The {@link ResultSet}
     * that is returned when this statement is executed should be updatable.
     *
     * @param connection    the connection to the underlying database
     * @param processorName the name of the processor to fetch the entry for
     * @param segment       the segment of the processor to fetch the entry for
     * @return a {@link PreparedStatement} that will fetch an updatable token entry when executed
     * @throws SQLException when an exception occurs while creating the prepared statement
     */
    protected PreparedStatement selectForUpdate(Connection connection, String processorName,
                                                int segment) throws SQLException {
        final String sql = "SELECT " +
                String.join(", ", schema.processorNameColumn(), schema.segmentColumn(), schema.tokenColumn(),
                            schema.tokenTypeColumn(), schema.timestampColumn(), schema.ownerColum()) + " FROM " +
                schema.tokenTable() + " WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() +
                " = ? FOR UPDATE";
        PreparedStatement preparedStatement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        preparedStatement.setString(1, processorName);
        preparedStatement.setInt(2, segment);
        return preparedStatement;
    }

    /**
     * If the given {@code resultSet} has no items this method should insert a new token entry. If a token already
     * exists it should be attempted to replace the token in the entry with the given {@code token} and claim ownership.
     *
     * @param resultSet     the updatable query result set of an executed {@link PreparedStatement}
     * @param token         the token for the new or updated entry
     * @param processorName the name of the processor owning the token
     * @param segment       the segment of the processor owning the token
     * @throws UnableToClaimTokenException if the token cannot be claimed because another node currently owns the token
     * @throws SQLException                when an exception occurs while updating the result set
     */
    protected void insertOrUpdateToken(ResultSet resultSet, TrackingToken token, String processorName,
                                       int segment) throws SQLException {
        if (resultSet.next()) {
            AbstractTokenEntry<?> entry = readTokenEntry(resultSet);
            entry.updateToken(token, serializer);
            resultSet.updateObject(schema.tokenColumn(), entry.getSerializedToken().getData());
            resultSet.updateString(schema.tokenTypeColumn(), entry.getSerializedToken().getType().getName());
            resultSet.updateString(schema.timestampColumn(), entry.timestampAsString());
            claimToken(resultSet, entry);
        } else {
            insertTokenEntry(resultSet, token, processorName, segment);
        }
    }

    /**
     * Tries to claim the given token {@code entry}. If the claim fails an {@link UnableToClaimTokenException} should be
     * thrown. Otherwise the given {@code resultSet} should be updated to reflect the claim.
     *
     * @param resultSet the updatable query result of an executed {@link PreparedStatement}
     * @param entry     the entry extracted from the given result set
     * @return the claimed tracking token
     * @throws UnableToClaimTokenException if the token cannot be claimed because another node currently owns the token
     * @throws SQLException                when an exception occurs while claiming the token entry
     */
    protected TrackingToken claimToken(ResultSet resultSet, AbstractTokenEntry<?> entry) throws SQLException {
        if (!entry.claim(nodeId, claimTimeout)) {
            throw new UnableToClaimTokenException(
                    format("Unable to claim token '%s[%s]'. It is owned by '%s'", entry.getProcessorName(),
                           entry.getSegment(), entry.getOwner()));
        }
        resultSet.updateString(schema.ownerColum(), entry.getOwner());
        resultSet.updateString(schema.timestampColumn(), entry.timestampAsString());
        resultSet.updateRow();
        return entry.getToken(serializer);
    }

    /**
     * Tries loading an existing token owned by a processor with given {@code processorName} and {@code segment}. If
     * such a token entry exists an attempt will be made to claim the token. If that succeeds the token will be
     * returned. If the token is already owned by another node an {@link UnableToClaimTokenException} will be thrown.
     * <p>
     * If no such token exists yet, a new token entry will be inserted with {@code null} token owned by this node and
     * return {@code null}.
     *
     * @param resultSet     the updatable result set from a prior select for update query
     * @param processorName the name of the processor to load or insert a token entry for
     * @param segment       the segment of the processor to load or insert a token entry for
     * @return the tracking token of the fetched entry or {@code null} if a new entry was inserted
     * @throws UnableToClaimTokenException if the token cannot be claimed because another node currently owns the token
     * @throws SQLException                when an exception occurs while loading or inserting the entry
     */
    protected TrackingToken loadOrInsertToken(ResultSet resultSet, String processorName,
                                              int segment) throws SQLException {
        if (!resultSet.next()) {
            return insertTokenEntry(resultSet, null, processorName, segment);
        }
        return claimToken(resultSet, readTokenEntry(resultSet));
    }

    /**
     * Inserts a new token entry via the given updatable {@code resultSet}.
     *
     * @param resultSet     the updatable result set to add the entry to
     * @param token         the token of the entry to insert
     * @param processorName the name of the processor to insert a token for
     * @param segment       the segment of the processor to insert a token for
     * @return the tracking token of the inserted entry
     * @throws SQLException when an exception occurs while inserting a token entry
     */
    protected TrackingToken insertTokenEntry(ResultSet resultSet, TrackingToken token, String processorName,
                                             int segment) throws SQLException {
        AbstractTokenEntry<?> entry = new GenericTokenEntry<>(token, serializer, contentType, processorName, segment);
        entry.claim(nodeId, claimTimeout);
        resultSet.moveToInsertRow();
        resultSet.updateObject(schema.tokenColumn(), token == null ? null : entry.getSerializedToken().getData());
        resultSet.updateString(schema.tokenTypeColumn(),
                               token == null ? null : entry.getSerializedToken().getType().getName());
        resultSet.updateString(schema.timestampColumn(), entry.timestampAsString());
        resultSet.updateString(schema.ownerColum(), entry.getOwner());
        resultSet.updateString(schema.processorNameColumn(), processorName);
        resultSet.updateInt(schema.segmentColumn(), segment);
        resultSet.insertRow();
        return token;
    }

    /**
     * Convert given {@code resultSet} to an {@link AbstractTokenEntry}. The result set contains a single token entry.
     *
     * @param resultSet the result set of a prior select statement containing a single token entry
     * @return an token entry with data extracted from the result set
     * @throws SQLException if the result set cannot be converted to an entry
     */
    protected AbstractTokenEntry<?> readTokenEntry(ResultSet resultSet) throws SQLException {
        return new GenericTokenEntry<>(readSerializedData(resultSet, schema.tokenColumn()),
                                       resultSet.getString(schema.tokenTypeColumn()),
                                       resultSet.getString(schema.timestampColumn()),
                                       resultSet.getString(schema.ownerColum()),
                                       resultSet.getString(schema.processorNameColumn()),
                                       resultSet.getInt(schema.segmentColumn()), contentType);
    }

    /**
     * Creates a new {@link PreparedStatement} to release the current claim this node has on a token belonging to a
     * processor with given {@code processorName} and {@code segment}.
     *
     * @param connection    the connection that should be used to create a {@link PreparedStatement}
     * @param processorName the name of the processor for which to release this node's claim
     * @param segment       the segment of the processor for which to release this node's claim
     * @return a {@link PreparedStatement} that will release the claim this node has on the token entry
     * @throws SQLException if the statement to release a claim cannot be created
     */
    protected PreparedStatement releaseClaim(Connection connection, String processorName,
                                             int segment) throws SQLException {
        final String sql =
                "UPDATE " + schema.tokenTable() + " SET " + schema.ownerColum() + " = ?, " + schema.timestampColumn() +
                        " = ? WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() +
                        " = ? AND " + schema.ownerColum() + " = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, null);
        preparedStatement.setString(2, AbstractTokenEntry.clock.instant().toString());
        preparedStatement.setString(3, processorName);
        preparedStatement.setInt(4, segment);
        preparedStatement.setString(5, nodeId);
        return preparedStatement;
    }

    /**
     * Returns the serialized token data from the given {@code resultSet} at given {@code columnName}.
     *
     * @param resultSet  the result set to get serialized data from
     * @param columnName the name of the column containing the serialized token
     * @param <T>        the type of data to return
     * @return the serialized data of the token
     * @throws SQLException if the token cannot be read from the entry
     */
    @SuppressWarnings("unchecked")
    protected <T> T readSerializedData(ResultSet resultSet, String columnName) throws SQLException {
        if (byte[].class.equals(contentType)) {
            return (T) resultSet.getBytes(columnName);
        }
        return (T) resultSet.getObject(columnName);
    }

    /**
     * Returns a {@link Connection} to the database.
     *
     * @return a database Connection
     */
    protected Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new JdbcException("Failed to obtain a database connection", e);
        }
    }


}
