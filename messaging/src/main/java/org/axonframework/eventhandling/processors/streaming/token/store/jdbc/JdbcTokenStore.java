/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.processors.streaming.token.store.jdbc;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.JdbcException;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.AbstractTokenEntry;
import org.axonframework.eventhandling.processors.streaming.token.store.ConfigToken;
import org.axonframework.eventhandling.processors.streaming.token.store.GenericTokenEntry;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToClaimTokenException;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToInitializeTokenException;
import org.axonframework.eventhandling.processors.streaming.token.store.UnableToRetrieveIdentifierException;
import org.axonframework.eventhandling.processors.streaming.token.store.jpa.TokenEntry;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.TemporalAmount;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.axonframework.common.jdbc.JdbcUtils.*;

/**
 * A {@link TokenStore} implementation that uses JDBC to save and load {@link TrackingToken} instances.
 * <p>
 * Before using this store make sure the database contains a table named {@link TokenSchema#tokenTable()} in which to
 * store the tokens. For convenience, this table can be constructed through the
 * {@link JdbcTokenStore#createSchema(TokenTableFactory)} operation.
 *
 * @author Rene de Waele
 * @since 3.0.0
 */
public class JdbcTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTokenStore.class);

    private static final String CONFIG_TOKEN_ID = "__config";
    private static final int CONFIG_SEGMENT = 0;
    private static final String COUNT_COLUMN_NAME = "segmentCount";
    private final ConnectionProvider connectionProvider;
    private final Serializer serializer;
    private final TokenSchema schema;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final Class<?> contentType;

    /**
     * Instantiate a {@code JdbcTokenStore} based on the fields contained in the
     * {@link JdbcTokenStoreConfiguration configuration}.
     * <p>
     * Will assert that the {@link ConnectionProvider}, {@link Serializer} and {@link JdbcTokenStoreConfiguration} are
     * not {@code null}, otherwise an {@link AxonConfigurationException} will be thrown.
     *
     * @param connectionProvider The {@link ConnectionProvider} used to provide connections to the underlying database.
     * @param serializer         The {@link Serializer} used to de-/serialize {@link TrackingToken}'s with.
     * @param configuration      The {@link JdbcTokenStoreConfiguration} used to instantiate a {@code JdbcTokenStore}
     *                           instance
     */
    public JdbcTokenStore(@Nonnull ConnectionProvider connectionProvider,
                          @Nonnull Serializer serializer,
                          @Nonnull JdbcTokenStoreConfiguration configuration) {
        assertNonNull(connectionProvider, "The ConnectionProvider is a hard requirement and should be provided");
        assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
        assertNonNull(configuration, "The JdbcTokenStoreConfiguration should be provided");
        this.connectionProvider = connectionProvider;
        this.serializer = serializer;
        this.schema = configuration.schema();
        this.claimTimeout = configuration.claimTimeout();
        this.nodeId = configuration.nodeId();
        this.contentType = configuration.contentType();
    }

    /**
     * Performs the DDL queries to create the schema necessary for this token store implementation.
     *
     * @param schemaFactory factory of the token entry schema
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

    @Nonnull
    @Override
    public CompletableFuture<Void> initializeTokenSegments(
            @Nonnull String processorName,
            int segmentCount,
            @Nullable TrackingToken initialToken,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            executeQuery(connection,
                         c -> selectForUpdate(c, processorName, 0),
                         resultSet -> {
                             for (int segment = 0; segment < segmentCount; segment++) {
                                 insertTokenEntry(connection, initialToken, processorName, segment);
                             }
                             return null;
                         },
                         e -> new UnableToClaimTokenException(
                                 "Could not initialize segments. Some segments were already present.", e
                         ));
        } finally {
            closeQuietly(connection);
        }
        return completedFuture(null);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> initializeSegment(
            @Nullable TrackingToken token,
            @Nonnull String processorName,
            int segment,
            @Nullable ProcessingContext context
    ) throws UnableToInitializeTokenException {
        Connection connection = getConnection();
        try {
            executeQuery(connection,
                         c -> selectForUpdate(c, processorName, 0),
                         resultSet -> {
                             insertTokenEntry(connection, token, processorName, segment);
                             return null;
                         },
                         e -> new UnableToInitializeTokenException(
                                 "Could not initialize segments. Some segments were already present.", e
                         ));
        } finally {
            closeQuietly(connection);
        }
        return completedFuture(null);
    }

    @Nonnull
    @Override
    public CompletableFuture<Optional<String>> retrieveStorageIdentifier(
            @Nullable ProcessingContext context
    ) throws UnableToRetrieveIdentifierException {
        return completedFuture(Optional.of(loadConfigurationToken()).map(configToken -> configToken.get("id")));
    }

    private ConfigToken loadConfigurationToken() throws UnableToRetrieveIdentifierException {
        Connection connection = getConnection();
        TrackingToken token;
        try {
            token = executeQuery(connection,
                                 c -> select(connection, CONFIG_TOKEN_ID, CONFIG_SEGMENT, false),
                                 resultSet -> {
                                     if (resultSet.next()) {
                                         return readTokenEntry(resultSet).getToken(serializer);
                                     } else {
                                         return null;
                                     }
                                 },
                                 e -> new UnableToRetrieveIdentifierException(
                                         "Exception while attempting to retrieve the config token",
                                         e),
                                 false);
            try {
                if (token == null) {
                    token = insertTokenEntry(connection,
                                             new ConfigToken(Collections.singletonMap(
                                                     "id", UUID.randomUUID().toString()
                                             )),
                                             CONFIG_TOKEN_ID,
                                             CONFIG_SEGMENT);
                }
            } catch (SQLException e) {
                throw new UnableToRetrieveIdentifierException(
                        "Exception while attempting to initialize the config token. It may have been concurrently initialized.",
                        e);
            }
        } finally {
            closeQuietly(connection);
        }
        return (ConfigToken) token;
    }

    /**
     * Returns the serializer used by the Token Store to serialize tokens.
     *
     * @return the serializer used by the Token Store to serialize tokens
     */
    public Serializer serializer() {
        return serializer;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> storeToken(
            @Nullable TrackingToken token,
            @Nonnull String processorName,
            int segment,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            int updatedToken = executeUpdate(
                    connection,
                    c -> storeUpdate(connection, token, processorName, segment),
                    e -> new JdbcException(format(
                            "Could not store token [%s] for processor [%s] and segment [%d]",
                            token, processorName, segment
                    ), e)
            );

            if (updatedToken == 0) {
                logger.debug("Could not update token [{}] for processor [{}] and segment [{}]. "
                                     + "Trying load-then-save approach instead.",
                             token, processorName, segment);
                executeQuery(
                        connection,
                        c -> selectForUpdate(c, processorName, segment),
                        resultSet -> {
                            updateToken(connection, resultSet, token, processorName, segment);
                            return null;
                        },
                        e -> new JdbcException(format(
                                "Could not store token [%s] for processor [%s] and segment [%d]",
                                token, processorName, segment
                        ), e)
                );
            }
        } finally {
            closeQuietly(connection);
        }
        return completedFuture(null);
    }

    @Nonnull
    @Override
    public CompletableFuture<TrackingToken> fetchToken(
            @Nonnull String processorName,
            int segment,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            return completedFuture(executeQuery(connection, c -> selectForUpdate(c, processorName, segment),
                                                resultSet -> loadToken(connection, resultSet, processorName, segment),
                                                e -> new JdbcException(
                                                        format("Could not load token for processor [%s] and segment [%d]",
                                                               processorName, segment), e))
            );
        } finally {
            closeQuietly(connection);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<TrackingToken> fetchToken(
            @Nonnull String processorName,
            @Nonnull Segment segment,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        Connection connection = getConnection();
        try {
            return completedFuture(executeQuery(connection,
                                                c -> selectForUpdate(c, processorName, segment.getSegmentId()),
                                                resultSet -> loadToken(connection, resultSet, processorName, segment),
                                                e -> new JdbcException(
                                                        format("Could not load token for processor [%s] and segment [%d]",
                                                               processorName, segment.getSegmentId()), e))
            );
        } finally {
            closeQuietly(connection);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> releaseClaim(@Nonnull String processorName,
                                                int segment,
                                                @Nullable ProcessingContext context) {
        Connection connection = getConnection();
        try {
            executeUpdates(connection, e -> {
                               throw new JdbcException(
                                       format("Could not load token for processor [%s] and segment " + "[%d]",
                                              processorName, segment), e);
                           },
                           c -> releaseClaim(c, processorName, segment));
        } finally {
            closeQuietly(connection);
        }
        return completedFuture(null);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> deleteToken(@Nonnull String processorName,
                                               int segment,
                                               @Nullable ProcessingContext context) {
        Connection connection = getConnection();
        try {
            int[] result = executeUpdates(connection, e -> {
                                              throw new JdbcException(
                                                      format("Could not remove token for processor [%s] and segment " + "[%d]",
                                                             processorName, segment), e);
                                          },
                                          c -> deleteToken(c, processorName, segment));
            if (result[0] < 1) {
                throw new UnableToClaimTokenException("Unable to claim token. It wasn't owned by " + nodeId);
            }
        } finally {
            closeQuietly(connection);
        }
        return completedFuture(null);
    }

    @Nonnull
    @Override
    public CompletableFuture<int[]> fetchSegments(@Nonnull String processorName,
                                                  @Nullable ProcessingContext context) {
        Connection connection = getConnection();
        try {
            return completedFuture(executeQuery(connection,
                                                c -> selectForSegments(c, processorName),
                                                listResults(rs -> rs.getInt(schema.segmentColumn())),
                                                e -> new JdbcException(format(
                                                        "Could not load segments for processor [%s]",
                                                        processorName
                                                ), e)
            ).stream().mapToInt(i -> i).toArray());
        } finally {
            closeQuietly(connection);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<List<Segment>> fetchAvailableSegments(@Nonnull String processorName,
                                                                   @Nullable ProcessingContext context) {
        Connection connection = getConnection();
        try {
            List<AbstractTokenEntry<?>> tokenEntries = executeQuery(connection,
                                                                    c -> selectTokenEntries(c, processorName),
                                                                    listResults(this::readTokenEntry),
                                                                    e -> new JdbcException(format(
                                                                            "Could not load segments for processor [%s]",
                                                                            processorName
                                                                    ), e)
            );
            int[] allSegments = tokenEntries.stream()
                                            .mapToInt(AbstractTokenEntry::getSegment)
                                            .toArray();
            return completedFuture(tokenEntries.stream()
                                               .filter(tokenEntry -> tokenEntry.mayClaim(nodeId, claimTimeout))
                                               .map(tokenEntry -> Segment.computeSegment(tokenEntry.getSegment(),
                                                                                         allSegments))
                                               .collect(Collectors.toList()));
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Returns a {@link PreparedStatement} to select all segments ids for a given processorName from the underlying
     * storage.
     *
     * @param connection    The connection to the underlying database.
     * @param processorName The name of the processor to fetch the segments for.
     * @return A {@link PreparedStatement} that will fetch segments when executed.
     * @throws SQLException When an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement selectForSegments(Connection connection, String processorName) throws SQLException {
        final String sql = "SELECT " + schema.segmentColumn() +
                " FROM " + schema.tokenTable() +
                " WHERE " + schema.processorNameColumn() + " = ?" +
                " ORDER BY " + schema.segmentColumn() + " ASC";
        PreparedStatement preparedStatement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        preparedStatement.setString(1, processorName);
        return preparedStatement;
    }

    /**
     * Returns a {@link PreparedStatement} to select all {@link TokenEntry TokenEntries} for a given processorName from
     * the underlying storage.
     *
     * @param connection    The connection to the underlying database.
     * @param processorName The name of the processor to fetch the segments for.
     * @return A {@link PreparedStatement} that will fetch TokenEntries when executed.
     * @throws SQLException When an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement selectTokenEntries(Connection connection, String processorName) throws SQLException {
        final String sql = "SELECT *" +
                " FROM " + schema.tokenTable() +
                " WHERE " + schema.processorNameColumn() + " = ?" +
                " ORDER BY " + schema.segmentColumn() + " ASC";
        PreparedStatement preparedStatement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        preparedStatement.setString(1, processorName);
        return preparedStatement;
    }

    /**
     * Returns a {@link PreparedStatement} which updates the given {@code token} for the given {@code processorName} and
     * {@code segment} combination.
     *
     * @param connection    The connection to the underlying database.
     * @param token         The new token to store.
     * @param processorName The name of the processor executing the update.
     * @param segment       The segment of the processor to executing the update.
     * @return A {@link PreparedStatement} that will update a token entry when executed.
     * @throws SQLException When an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement storeUpdate(Connection connection,
                                            TrackingToken token,
                                            String processorName,
                                            int segment) throws SQLException {
        AbstractTokenEntry<?> tokenToStore =
                new GenericTokenEntry<>(token, serializer, contentType, processorName, segment);
        Object tokenDataToStore = getOrDefault(tokenToStore.getSerializedToken(), SerializedObject::getData, null);
        String tokenTypeToStore = getOrDefault(tokenToStore.getTokenType(), SerializedType::getName, null);

        final String sql = "UPDATE " + schema.tokenTable() + " SET "
                + schema.tokenColumn() + " = ?, "
                + schema.tokenTypeColumn() + " = ?, "
                + schema.timestampColumn() + " = ? "
                + "WHERE " + schema.ownerColumn() + " = ? "
                + "AND " + schema.processorNameColumn() + " = ? "
                + "AND " + schema.segmentColumn() + " = ? ";
        PreparedStatement preparedStatement = connection.prepareStatement(
                sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY
        );
        preparedStatement.setObject(1, tokenDataToStore);
        preparedStatement.setString(2, tokenTypeToStore);
        preparedStatement.setString(3, tokenToStore.timestampAsString());
        preparedStatement.setString(4, nodeId);
        preparedStatement.setString(5, processorName);
        preparedStatement.setInt(6, segment);
        return preparedStatement;
    }

    /**
     * Returns a {@link PreparedStatement} to select a token entry from the underlying storage. The {@link ResultSet}
     * that is returned when this statement is executed should be updatable.
     *
     * @param connection    The connection to the underlying database.
     * @param processorName The name of the processor to fetch the entry for.
     * @param segment       The segment of the processor to fetch the entry for.
     * @return A {@link PreparedStatement} that will fetch an updatable token entry when executed.
     * @throws SQLException When an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement selectForUpdate(Connection connection,
                                                String processorName,
                                                int segment) throws SQLException {
        return select(connection, processorName, segment, true);
    }

    /**
     * Returns a {@link PreparedStatement} to select a token entry from the underlying storage, either for updating or
     * just for reading.
     *
     * @param connection    The connection to the underlying database.
     * @param processorName The name of the processor to fetch the entry for.
     * @param segment       The segment of the processor to fetch the entry for.
     * @param forUpdate     Whether the returned token should be updatable.
     * @return A {@link PreparedStatement} that will fetch an updatable token entry when executed.
     * @throws SQLException When an exception occurs while creating the prepared statement.
     */
    protected PreparedStatement select(Connection connection, String processorName,
                                       int segment, boolean forUpdate) throws SQLException {
        final String sql = "SELECT " +
                String.join(", ", schema.processorNameColumn(), schema.segmentColumn(), schema.tokenColumn(),
                            schema.tokenTypeColumn(), schema.timestampColumn(), schema.ownerColumn()) + " FROM " +
                schema.tokenTable() + " WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() +
                " = ? " + (forUpdate ? "FOR UPDATE" : "");
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, processorName);
        preparedStatement.setInt(2, segment);
        return preparedStatement;
    }

    /**
     * If the given {@code resultSet} has an entry, attempts to replace the token in the entry with the given
     * {@code token} and claim ownership.
     *
     * @param connection    The connection to the underlying database.
     * @param resultSet     The updatable query result set of an executed {@link PreparedStatement}.
     * @param token         The token for the new or updated entry.
     * @param processorName The name of the processor owning the token.
     * @param segment       The segment of the processor owning the token.
     * @throws UnableToClaimTokenException If the token cannot be claimed because another node currently owns the
     *                                     token.
     * @throws SQLException                When an exception occurs while updating the result set.
     */
    protected void updateToken(Connection connection,
                               ResultSet resultSet,
                               TrackingToken token,
                               String processorName,
                               int segment) throws SQLException {
        final String sql = "UPDATE " + schema.tokenTable() + " SET " + schema.ownerColumn() + " = ?, " +
                schema.tokenColumn() + " = ?, " + schema.tokenTypeColumn() + " = ?, " + schema.timestampColumn() +
                " = ? WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() + " = ?";
        if (resultSet.next()) {
            AbstractTokenEntry<?> entry = readTokenEntry(resultSet);
            entry.updateToken(token, serializer);

            if (!entry.claim(nodeId, claimTimeout)) {
                throw new UnableToClaimTokenException(
                        format("Unable to claim token '%s[%s]'. It is owned by '%s'", entry.getProcessorName(),
                               entry.getSegment(), entry.getOwner()));
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, entry.getOwner());
                preparedStatement.setObject(2, entry.getSerializedToken().getData());
                preparedStatement.setString(3, entry.getSerializedToken().getType().getName());
                preparedStatement.setString(4, entry.timestampAsString());
                preparedStatement.setString(5, processorName);
                preparedStatement.setInt(6, segment);
                if (preparedStatement.executeUpdate() != 1) {
                    throw new UnableToClaimTokenException(format("Unable to claim token '%s[%s]'. It has been removed",
                                                                 processorName, segment));
                }
            }
        } else {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has not been initialized yet", processorName, segment
            ));
        }
    }

    /**
     * Tries to claim the given token {@code entry}. If the claim fails an {@link UnableToClaimTokenException} should be
     * thrown. Otherwise, the given {@code resultSet} should be updated to reflect the claim.
     *
     * @param connection The connection to the underlying database.
     * @param entry      The entry extracted from the given result set.
     * @return The claimed tracking token.
     * @throws UnableToClaimTokenException If the token cannot be claimed because another node currently owns the
     *                                     token.
     * @throws SQLException                When an exception occurs while claiming the token entry.
     */
    protected TrackingToken claimToken(Connection connection, AbstractTokenEntry<?> entry) throws SQLException {
        final String sql = "UPDATE " + schema.tokenTable() + " SET " + schema.ownerColumn() + " = ?, " +
                schema.timestampColumn() + " = ? WHERE " + schema.processorNameColumn() + " = ? AND " +
                schema.segmentColumn() + " = ?";
        if (!entry.claim(nodeId, claimTimeout)) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It is owned by '%s'",
                    entry.getProcessorName(), entry.getSegment(), entry.getOwner()
            ));
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, entry.getOwner());
            preparedStatement.setString(2, entry.timestampAsString());
            preparedStatement.setString(3, entry.getProcessorName());
            preparedStatement.setInt(4, entry.getSegment());
            if (preparedStatement.executeUpdate() != 1) {
                throw new UnableToClaimTokenException(
                        format("Unable to claim token '%s[%s]'. It has been removed", entry.getProcessorName(),
                               entry.getSegment()));
            }
        }

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
     * @param connection    The connection to the underlying database.
     * @param resultSet     The updatable result set from a prior select for update query.
     * @param processorName The name of the processor to load or insert a token entry for.
     * @param segment       The segment of the processor to load or insert a token entry for.
     * @return The tracking token of the fetched entry or {@code null} if a new entry was inserted.
     * @throws UnableToClaimTokenException If the token cannot be claimed because another node currently owns the
     *                                     token.
     * @throws SQLException                When an exception occurs while loading or inserting the entry.
     */
    protected TrackingToken loadToken(Connection connection,
                                      ResultSet resultSet,
                                      String processorName,
                                      int segment) throws SQLException {
        if (!resultSet.next()) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has not been initialized yet", processorName, segment
            ));
        }
        return claimToken(connection, readTokenEntry(resultSet));
    }

    /**
     * Tries to load an existing token owned by a processor with given {@code processorName} and {@code segment}. If
     * such a token entry exists, an attempt will be made to claim the token. If that succeeds, the token will be
     * returned. If the token is already owned by another node an {@link UnableToClaimTokenException} will be thrown.
     * <p>
     * If no such token exists yet, a new token entry will be inserted with a {@code null} token, owned by this node,
     * and this method returns {@code null}.
     * <p>
     * If a token has been claimed, the {@code segment} will be validated by checking the database for the split and
     * merge candidate segments. If a concurrent split or merge operation has been detected, the claim will be released
     * and an {@link UnableToClaimTokenException} will be thrown.
     *
     * @param connection    The connection to the underlying database.
     * @param resultSet     The updatable result set from a prior select for update query.
     * @param processorName The name of the processor to load or insert a token entry for.
     * @param segment       The segment of the processor to load or insert a token entry for.
     * @return The tracking token of the fetched entry or {@code null} if a new entry was inserted.
     * @throws UnableToClaimTokenException If the token cannot be claimed because another node currently owns the token
     *                                     or if the segment has been split or merged concurrently.
     * @throws SQLException                When an exception occurs while loading or inserting the entry.
     */
    protected TrackingToken loadToken(Connection connection,
                                      ResultSet resultSet,
                                      String processorName,
                                      Segment segment) throws SQLException {
        if (!resultSet.next()) {
            throw new UnableToClaimTokenException(
                    format("Unable to claim token '%s[%s]'. It has not been initialized yet", processorName,
                           segment.getSegmentId()));
        }
        AbstractTokenEntry<?> tokenEntry = readTokenEntry(resultSet);
        validateSegment(processorName, segment);
        return claimToken(connection, tokenEntry);
    }

    /**
     * Validate a {@code segment} by checking for the existence of a split or merge candidate segment.
     * <p>
     * If the segment has been split concurrently, the split segment candidate will be found, indicating that we have
     * claimed an incorrect {@code segment}. If the segment has been merged concurrently, the merge candidate segment
     * will no longer exist, also indicating that we have claimed an incorrect {@code segment}.
     *
     * @param processorName The name of the processor to load or insert a token entry for,
     * @param segment       The segment of the processor to load or insert a token entry for,
     */
    protected void validateSegment(String processorName, Segment segment) {
        Connection connection = getConnection();
        try {
            int splitSegmentId = segment.splitSegmentId(); // This segment should not exist
            int mergeableSegmentId = segment.mergeableSegmentId(); // This segment should exist
            executeQuery(connection, c -> selectSegments(c, processorName, splitSegmentId, mergeableSegmentId),
                         r -> containsOneElement(r, processorName, segment.getSegmentId()),
                         e -> new JdbcException(format("Could not load segments for processor [%s]", processorName),
                                                e));
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Returns a {@link PreparedStatement} for the count of segments that can be found after searching for the
     * {@code splitSegmentId} and {@code mergeableSegmentId}.
     *
     * @param connection         The connection to the underlying database.
     * @param processorName      The name of the processor to load or insert a token entry for.
     * @param splitSegmentId     The id of the split candidate segment.
     * @param mergeableSegmentId The id of the merge candidate segment.
     * @return The PreparedStatement to execute.
     * @throws SQLException When an Exception occurs in building the {@link PreparedStatement}.
     */
    protected PreparedStatement selectSegments(Connection connection,
                                               String processorName,
                                               int splitSegmentId,
                                               int mergeableSegmentId) throws SQLException {
        final String sql = "SELECT count(*) as " + COUNT_COLUMN_NAME
                + " FROM " + schema.tokenTable()
                + " WHERE " + schema.processorNameColumn() + " = ? AND (" + schema.segmentColumn() +
                " = ? OR " + schema.segmentColumn() + " = ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, processorName);
        preparedStatement.setInt(2, splitSegmentId);
        preparedStatement.setInt(3, mergeableSegmentId);
        return preparedStatement;
    }

    /**
     * Confirm that the first row of the {@code resultSet} only contains a size of 1, indicating that the desired number
     * of segments has been found.
     *
     * @param resultSet     The ResultSet returned by the {@code selectSegments} method.
     * @param processorName The process name for which a token has been fetched.
     * @param segmentId     The segment we are validating.
     * @return True if only a single segment was found.
     * @throws SQLException When an exception occurs processing the {@code resultSet}.
     */
    private boolean containsOneElement(ResultSet resultSet, String processorName, int segmentId) throws SQLException {
        resultSet.next();
        int size = resultSet.getInt(COUNT_COLUMN_NAME);
        if (size == 0) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has been merged with another segment",
                    processorName,
                    segmentId));
        }
        if (size >= 2) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has been split into two segments",
                    processorName,
                    segmentId));
        }
        return true;
    }

    /**
     * Inserts a new token entry via the given updatable {@code resultSet}.
     *
     * @param connection    The connection to the underlying database.
     * @param token         The token of the entry to insert.
     * @param processorName The name of the processor to insert a token for.
     * @param segment       The segment of the processor to insert a token for.
     * @return The tracking token of the inserted entry.
     * @throws SQLException When an exception occurs while inserting a token entry.
     */
    protected TrackingToken insertTokenEntry(Connection connection,
                                             TrackingToken token,
                                             String processorName,
                                             int segment) throws SQLException {
        final String sql = "INSERT INTO " + schema.tokenTable() + " (" + schema.processorNameColumn() + "," +
                schema.segmentColumn() + "," + schema.timestampColumn() + "," + schema.tokenColumn() + "," +
                schema.tokenTypeColumn() + "," + schema.ownerColumn() + ") VALUES (?,?,?,?,?,?)";
        AbstractTokenEntry<?> entry = new GenericTokenEntry<>(token, serializer, contentType, processorName, segment);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, processorName);
            preparedStatement.setInt(2, segment);
            preparedStatement.setString(3, entry.timestampAsString());
            preparedStatement.setObject(4, token == null ? null : entry.getSerializedToken().getData());
            preparedStatement.setString(5, token == null ? null : entry.getSerializedToken().getType().getName());
            preparedStatement.setString(6, entry.getOwner());
            preparedStatement.executeUpdate();
        }

        return token;
    }

    /**
     * Convert given {@code resultSet} to an {@link AbstractTokenEntry}. The result set contains a single token entry.
     *
     * @param resultSet The result set of a prior select statement containing a single token entry.
     * @return A token entry with data extracted from the result set.
     * @throws SQLException If the result set cannot be converted to an entry.
     */
    protected AbstractTokenEntry<?> readTokenEntry(ResultSet resultSet) throws SQLException {
        return new GenericTokenEntry<>(readSerializedData(resultSet, schema.tokenColumn()),
                                       resultSet.getString(schema.tokenTypeColumn()),
                                       resultSet.getString(schema.timestampColumn()),
                                       resultSet.getString(schema.ownerColumn()),
                                       resultSet.getString(schema.processorNameColumn()),
                                       resultSet.getInt(schema.segmentColumn()), contentType);
    }

    /**
     * Creates a new {@link PreparedStatement} to release the current claim this node has on a token belonging to a
     * processor with given {@code processorName} and {@code segment}.
     *
     * @param connection    The connection that should be used to create a {@link PreparedStatement}.
     * @param processorName The name of the processor for which to release this node's claim.
     * @param segment       The segment of the processor for which to release this node's claim.
     * @return A {@link PreparedStatement} that will release the claim this node has on the token entry.
     * @throws SQLException If the statement to release a claim cannot be created.
     */
    protected PreparedStatement releaseClaim(Connection connection,
                                             String processorName,
                                             int segment) throws SQLException {
        final String sql =
                "UPDATE " + schema.tokenTable() + " SET " + schema.ownerColumn() + " = ?, " + schema.timestampColumn() +
                        " = ? WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() +
                        " = ? AND " + schema.ownerColumn() + " = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, null);
        preparedStatement.setString(2, formatInstant(AbstractTokenEntry.clock.instant()));
        preparedStatement.setString(3, processorName);
        preparedStatement.setInt(4, segment);
        preparedStatement.setString(5, nodeId);
        return preparedStatement;
    }

    /**
     * Creates a new {@link PreparedStatement} to release the current claim this node has on a token belonging to a
     * processor with given {@code processorName} and {@code segment}.
     *
     * @param connection    The connection that should be used to create a {@link PreparedStatement}.
     * @param processorName The name of the processor for which to release this node's claim.
     * @param segment       The segment of the processor for which to release this node's claim.
     * @return A {@link PreparedStatement} that will release the claim this node has on the token entry.
     * @throws SQLException If the statement to release a claim cannot be created.
     */
    protected PreparedStatement deleteToken(Connection connection,
                                            String processorName,
                                            int segment) throws SQLException {
        final String sql =
                "DELETE FROM " + schema.tokenTable() +
                        " WHERE " + schema.processorNameColumn() + " = ? AND " + schema.segmentColumn() +
                        " = ? AND " + schema.ownerColumn() + " = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, processorName);
        preparedStatement.setInt(2, segment);
        preparedStatement.setString(3, nodeId);
        return preparedStatement;
    }


    /**
     * Returns the serialized token data from the given {@code resultSet} at given {@code columnName}.
     *
     * @param resultSet  The result set to get serialized data from.
     * @param columnName The name of the column containing the serialized token.
     * @param <T>        The type of data to return.
     * @return The serialized data of the token.
     * @throws SQLException If the token cannot be read from the entry.
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