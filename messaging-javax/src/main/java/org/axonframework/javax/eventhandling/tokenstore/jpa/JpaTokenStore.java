/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.javax.eventhandling.tokenstore.jpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.ConfigToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.UnableToInitializeTokenException;
import org.axonframework.eventhandling.tokenstore.UnableToRetrieveIdentifierException;
import org.axonframework.javax.common.jpa.EntityManagerProvider;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of a token store that uses JPA to save and load tokens. This implementation uses {@link TokenEntry}
 * entities.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class JpaTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(JpaTokenStore.class);
    private static final String CONFIG_TOKEN_ID = "__config";
    private static final int CONFIG_SEGMENT = 0;

    private final EntityManagerProvider entityManagerProvider;
    private final Serializer serializer;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final LockModeType loadingLockMode;

    /**
     * Instantiate a Builder to be able to create a {@link JpaTokenStore}.
     * <p>
     * The {@code claimTimeout} to a 10 seconds duration, and {@code nodeId} is defaulted to the name of the managed
     * bean for the runtime system of the Java virtual machine. The {@link EntityManagerProvider} and {@link Serializer}
     * are a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link JpaTokenStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link JpaTokenStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EntityManager}, {@link Serializer}, {@code claimTimeout} and {@code nodeId} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JpaTokenStore} instance
     */
    protected JpaTokenStore(Builder builder) {
        builder.validate();
        this.entityManagerProvider = builder.entityManagerProvider;
        this.serializer = builder.serializer;
        this.claimTimeout = builder.claimTimeout;
        this.nodeId = builder.nodeId;
        this.loadingLockMode = builder.loadingLockMode;
    }

    @Override
    public void initializeTokenSegments(@Nonnull String processorName, int segmentCount)
            throws UnableToClaimTokenException {
        initializeTokenSegments(processorName, segmentCount, null);
    }

    @Override
    public void initializeTokenSegments(@Nonnull String processorName, int segmentCount,
                                        @Nullable TrackingToken initialToken)
            throws UnableToClaimTokenException {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        if (fetchSegments(processorName).length > 0) {
            throw new UnableToClaimTokenException("Could not initialize segments. Some segments were already present.");
        }
        for (int segment = 0; segment < segmentCount; segment++) {
            TokenEntry token = new TokenEntry(processorName, segment, initialToken, serializer);
            entityManager.persist(token);
        }
        entityManager.flush();
    }

    @Override
    public void storeToken(@Nullable TrackingToken token, @Nonnull String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        TokenEntry tokenToStore = new TokenEntry(processorName, segment, token, serializer);
        byte[] tokenDataToStore =
                getOrDefault(tokenToStore.getSerializedToken(), SerializedObject::getData, null);
        String tokenTypeToStore = getOrDefault(tokenToStore.getTokenType(), SerializedType::getName, null);

        int updatedTokens = entityManager.createQuery("UPDATE TokenEntry te SET "
                                                              + "te.token = :token, "
                                                              + "te.tokenType = :tokenType, "
                                                              + "te.timestamp = :timestamp "
                                                              + "WHERE te.owner = :owner "
                                                              + "AND te.processorName = :processorName "
                                                              + "AND te.segment = :segment")
                                         .setParameter("token", tokenDataToStore)
                                         .setParameter("tokenType", tokenTypeToStore)
                                         .setParameter("timestamp", tokenToStore.timestampAsString())
                                         .setParameter("owner", nodeId)
                                         .setParameter("processorName", processorName)
                                         .setParameter("segment", segment)
                                         .executeUpdate();

        if (updatedTokens == 0) {
            logger.debug("Could not update token [{}] for processor [{}] and segment [{}]. "
                                 + "Trying load-then-save approach instead.",
                         token, processorName, segment);
            TokenEntry tokenEntry = loadToken(processorName, segment, entityManager);
            tokenEntry.updateToken(token, serializer);
        }
    }

    @Override
    public void releaseClaim(@Nonnull String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        entityManager.createQuery(
                             "UPDATE TokenEntry te SET te.owner = null " +
                                     "WHERE te.owner = :owner AND te.processorName = :processorName " +
                                     "AND te.segment = :segment")
                     .setParameter("processorName", processorName).setParameter("segment", segment)
                     .setParameter("owner", nodeId)
                     .executeUpdate();
    }

    @Override
    public void initializeSegment(@Nullable TrackingToken token, @Nonnull String processorName, int segment)
            throws UnableToInitializeTokenException {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        TokenEntry entry = new TokenEntry(processorName, segment, token, serializer);
        entityManager.persist(entry);
        entityManager.flush();
    }

    @Override
    public boolean requiresExplicitSegmentInitialization() {
        return true;
    }

    @Override
    public void deleteToken(@Nonnull String processorName, int segment) throws UnableToClaimTokenException {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        int updates = entityManager.createQuery(
                                           "DELETE FROM TokenEntry te " +
                                                   "WHERE te.owner = :owner AND te.processorName = :processorName " +
                                                   "AND te.segment = :segment")
                                   .setParameter("processorName", processorName)
                                   .setParameter("segment", segment)
                                   .setParameter("owner", nodeId)
                                   .executeUpdate();

        if (updates == 0) {
            throw new UnableToClaimTokenException("Unable to remove token. It is not owned by " + nodeId);
        }
    }

    @Override
    public TrackingToken fetchToken(@Nonnull String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        return loadToken(processorName, segment, entityManager).getToken(serializer);
    }

    @Override
    public TrackingToken fetchToken(@Nonnull String processorName, @Nonnull Segment segment)
            throws UnableToClaimTokenException {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        return loadToken(processorName, segment, entityManager).getToken(serializer);
    }

    @Override
    public void extendClaim(@Nonnull String processorName, int segment) throws UnableToClaimTokenException {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        int updates = entityManager.createQuery("UPDATE TokenEntry te SET te.timestamp = :timestamp " +
                                                        "WHERE te.processorName = :processorName " +
                                                        "AND te.segment = :segment " +
                                                        "AND te.owner = :owner")
                                   .setParameter("processorName", processorName)
                                   .setParameter("segment", segment)
                                   .setParameter("owner", nodeId)
                                   .setParameter("timestamp", formatInstant(TokenEntry.clock.instant()))
                                   .executeUpdate();

        if (updates == 0) {
            throw new UnableToClaimTokenException("Unable to extend the claim on token for processor '" +
                                                          processorName + "[" + segment + "]'. It is either claimed " +
                                                          "by another process, or there is no such token.");
        }
    }

    @Override
    public int[] fetchSegments(@Nonnull String processorName) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        final List<Integer> resultList = entityManager.createQuery(
                "SELECT te.segment FROM TokenEntry te "
                        + "WHERE te.processorName = :processorName ORDER BY te.segment ASC",
                Integer.class
        ).setParameter("processorName", processorName).getResultList();

        return resultList.stream().mapToInt(i -> i).toArray();
    }

    @Override
    public List<Segment> fetchAvailableSegments(@Nonnull String processorName) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        final List<TokenEntry> resultList = entityManager.createQuery(
                "SELECT te FROM TokenEntry te "
                        + "WHERE te.processorName = :processorName ORDER BY te.segment ASC",
                TokenEntry.class
        ).setParameter("processorName", processorName).getResultList();
        int[] allSegments = resultList.stream()
                                      .mapToInt(TokenEntry::getSegment)
                                      .toArray();
        return resultList.stream()
                         .filter(tokenEntry -> tokenEntry.mayClaim(nodeId, claimTimeout))
                         .map(tokenEntry -> Segment.computeSegment(tokenEntry.getSegment(), allSegments))
                         .collect(Collectors.toList());
    }

    /**
     * Loads an existing {@link TokenEntry} or creates a new one using the given {@code entityManager} for given
     * {@code processorName} and {@code segment}.
     *
     * @param processorName the name of the event processor
     * @param segment       the segment of the event processor
     * @param entityManager the entity manager instance to use for the query
     * @return the token entry for the given processor name and segment
     * @throws UnableToClaimTokenException if there is a token for given {@code processorName} and {@code segment}, but
     *                                     it is claimed by another process.
     */
    protected TokenEntry loadToken(String processorName, int segment, EntityManager entityManager) {
        TokenEntry token = entityManager
                .find(TokenEntry.class, new TokenEntry.PK(processorName, segment), loadingLockMode);

        if (token == null) {
            throw new UnableToClaimTokenException(
                    format("Unable to claim token '%s[%s]'. It has not been initialized yet", processorName,
                           segment));
        } else if (!token.claim(nodeId, claimTimeout)) {
            throw new UnableToClaimTokenException(
                    format("Unable to claim token '%s[%s]'. It is owned by '%s'", processorName,
                           segment, token.getOwner()));
        }
        return token;
    }

    /**
     * Tries loading an existing token owned by a processor with given {@code processorName} and {@code segment}. If
     * such a token entry exists an attempt will be made to claim the token. If that succeeds the token will be
     * returned. If the token is already owned by another node an {@link UnableToClaimTokenException} will be thrown.
     * <p>
     * If no such token exists yet, a new token entry will be inserted with a {@code null} token, owned by this node,
     * and this method returns {@code null}.
     * <p>
     * If a token has been claimed, the {@code segment} will be validated by checking the database for the split and
     * merge candidate segments. If a concurrent split or merge operation has been detected, the calim will be released
     * and an {@link UnableToClaimTokenException} will be thrown.}
     *
     * @param processorName the name of the processor to load or insert a token entry for
     * @param segment       the segment of the processor to load or insert a token entry for
     * @param entityManager the entity manager instance to use for the query
     * @return the tracking token of the fetched entry or {@code null} if a new entry was inserted
     * @throws UnableToClaimTokenException if the token cannot be claimed because another node currently owns the token
     *                                     or if the segment has been split or merged concurrently
     */
    protected TokenEntry loadToken(String processorName, Segment segment, EntityManager entityManager) {
        TokenEntry token = loadToken(processorName, segment.getSegmentId(), entityManager);
        try {
            validateSegment(processorName, segment, entityManager);
        } catch (UnableToClaimTokenException e) {
            token.releaseClaim(nodeId);
            throw e;
        }
        return token;
    }

    /**
     * Validate a {@code segment} by checking for the existence of a split or merge candidate segment.
     * <p>
     * If the segment has been split concurrently, the split segment candidate will be found, indicating that we have
     * claimed an incorrect {@code segment}. If the segment has been merged concurrently, the merge candidate segment
     * will no longer exist, also indicating that we have claimed an incorrect {@code segment}.
     *
     * @param processorName the name of the processor to load or insert a token entry for
     * @param segment       the segment of the processor to load or insert a token entry for
     */
    private void validateSegment(String processorName, Segment segment, EntityManager entityManager) {
        TokenEntry mergeableSegment = entityManager //This segment should exist
                                                    .find(TokenEntry.class,
                                                          new TokenEntry.PK(processorName,
                                                                            segment.mergeableSegmentId()),
                                                          loadingLockMode);
        if (mergeableSegment == null) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has been merged with another segment",
                    processorName,
                    segment.getSegmentId()));
        }
        TokenEntry splitSegment = entityManager //This segment should not exist
                                                .find(TokenEntry.class,
                                                      new TokenEntry.PK(processorName, segment.splitSegmentId()),
                                                      loadingLockMode);
        if (splitSegment != null) {
            throw new UnableToClaimTokenException(format(
                    "Unable to claim token '%s[%s]'. It has been split into two segments",
                    processorName,
                    segment.getSegmentId()));
        }
    }

    @Override
    public Optional<String> retrieveStorageIdentifier() {
        try {
            return Optional.of(getConfig()).map(i -> i.get("id"));
        } catch (Exception e) {
            throw new UnableToRetrieveIdentifierException(
                    "Exception occurred while trying to establish storage identifier",
                    e);
        }
    }

    private ConfigToken getConfig() {
        EntityManager em = entityManagerProvider.getEntityManager();
        TokenEntry token = em
                .find(TokenEntry.class, new TokenEntry.PK(CONFIG_TOKEN_ID, CONFIG_SEGMENT), LockModeType.NONE);
        if (token == null) {
            token = new TokenEntry(CONFIG_TOKEN_ID,
                                   CONFIG_SEGMENT,
                                   new ConfigToken(Collections.singletonMap("id", UUID.randomUUID().toString())),
                                   serializer);
            em.persist(token);
            em.flush();
        }
        return (ConfigToken) token.getToken(serializer);
    }

    /**
     * Returns the serializer used by the Token Store to serialize tokens.
     *
     * @return the serializer used by the Token Store to serialize tokens
     */
    public Serializer serializer() {
        return serializer;
    }

    /**
     * Builder class to instantiate a {@link JpaTokenStore}.
     * <p>
     * The {@code claimTimeout} to a 10 seconds duration, and {@code nodeId} is defaulted to the name of the managed
     * bean for the runtime system of the Java virtual machine. The {@link EntityManagerProvider} and {@link Serializer}
     * are a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private LockModeType loadingLockMode = LockModeType.PESSIMISTIC_WRITE;
        private EntityManagerProvider entityManagerProvider;
        private Serializer serializer;
        private TemporalAmount claimTimeout = Duration.ofSeconds(10);
        private String nodeId = ManagementFactory.getRuntimeMXBean().getName();

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the underlying
         * database.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager} used to
         *                              access the underlying database
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder entityManagerProvider(EntityManagerProvider entityManagerProvider) {
            assertNonNull(entityManagerProvider, "EntityManagerProvider may not be null");
            this.entityManagerProvider = entityManagerProvider;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize {@link TrackingToken}s with.
         *
         * @param serializer a {@link Serializer} used to de-/serialize {@link TrackingToken}s with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@code claimTimeout} specifying the amount of time this process will wait after which this process
         * will force a claim of a {@link TrackingToken}. Thus if a claim has not been updated for the given
         * {@code claimTimeout}, this process will 'steal' the claim. Defaults to a duration of 10 seconds.
         *
         * @param claimTimeout a timeout specifying the time after which this process will force a claim
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder claimTimeout(TemporalAmount claimTimeout) {
            assertNonNull(claimTimeout, "The claim timeout may not be null");
            this.claimTimeout = claimTimeout;
            return this;
        }

        /**
         * Sets the {@code nodeId} to identify ownership of the tokens. Defaults to the name of the managed bean for the
         * runtime system of the Java virtual machine
         *
         * @param nodeId the id as a {@link String} to identify ownership of the tokens
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder nodeId(String nodeId) {
            assertNodeId(nodeId, "The nodeId may not be null or empty");
            this.nodeId = nodeId;
            return this;
        }

        /**
         * The {@link LockModeType} to use when loading tokens from the underlying database. Defaults to
         * {@code LockModeType.PESSIMISTIC_WRITE}, to force a write lock, which prevents lock upgrading and potential
         * resulting deadlocks.
         *
         * @param loadingLockMode The lock mode to use when retrieving tokens from the underlying store
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder loadingLockMode(LockModeType loadingLockMode) {
            this.loadingLockMode = loadingLockMode;
            return this;
        }

        /**
         * Initializes a {@link JpaTokenStore} as specified through this Builder.
         *
         * @return a {@link JpaTokenStore} as specified through this Builder
         */
        public JpaTokenStore build() {
            return new JpaTokenStore(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(entityManagerProvider,
                          "The EntityManagerProvider is a hard requirement and should be provided");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
            assertNodeId(nodeId, "The nodeId is a hard requirement and should be provided");
        }

        private void assertNodeId(String nodeId, String exceptionMessage) {
            assertThat(nodeId, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
        }
    }
}
