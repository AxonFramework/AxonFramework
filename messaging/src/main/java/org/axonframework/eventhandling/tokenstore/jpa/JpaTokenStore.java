/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Implementation of a token store that uses JPA to save and load tokens. This implementation uses {@link TokenEntry}
 * entities.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class JpaTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(JpaTokenStore.class);

    private final EntityManagerProvider entityManagerProvider;
    private final Serializer serializer;
    private final TemporalAmount claimTimeout;
    private final String nodeId;

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
    }

    /**
     * Instantiate a Builder to be able to create a {@link JpaTokenStore}.
     * <p>
     * The {@code claimTimeout} to a 10 seconds duration, and the {@code nodeId} is defaulted to the
     * {@link ManagementFactory#getRuntimeMXBean#getName} output. The {@link EntityManagerProvider} and
     * {@link Serializer} are a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link JpaTokenStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount) throws UnableToClaimTokenException {
        initializeTokenSegments(processorName, segmentCount, null);
    }

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount, TrackingToken initialToken)
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
    public void storeToken(TrackingToken token, String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        TokenEntry tokenEntry = loadOrCreateToken(processorName, segment, entityManager);
        tokenEntry.updateToken(token, serializer);
    }

    @Override
    public void releaseClaim(String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        int updates = entityManager.createQuery(
                "UPDATE TokenEntry te SET te.owner = null " +
                        "WHERE te.owner = :owner AND te.processorName = :processorName " +
                        "AND te.segment = :segment"
        ).setParameter("processorName", processorName).setParameter("segment", segment).setParameter("owner", nodeId)
                                   .executeUpdate();

        if (updates == 0) {
            logger.warn("Releasing claim of token {}/{} failed. It was not owned by {}", processorName, segment,
                        nodeId);
        }
    }

    @Override
    public TrackingToken fetchToken(String processorName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        return loadOrCreateToken(processorName, segment, entityManager).getToken(serializer);
    }

    @Override
    public void extendClaim(String processorName, int segment) throws UnableToClaimTokenException {
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
    public int[] fetchSegments(String processorName) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();

        final List<Integer> resultList = entityManager.createQuery(
                "SELECT te.segment FROM TokenEntry te "
                        + "WHERE te.processorName = :processorName ORDER BY te.segment ASC",
                Integer.class
        ).setParameter("processorName", processorName).getResultList();

        return resultList.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Loads an existing {@link TokenEntry} or creates a new one using the given {@code entityManager} for given {@code
     * processorName} and {@code segment}.
     *
     * @param processorName the name of the event processor
     * @param segment       the segment of the event processor
     * @param entityManager the entity manager instance to use for the query
     * @return the token entry for the given processor name and segment
     *
     * @throws UnableToClaimTokenException if there is a token for given {@code processorName} and {@code segment}, but
     *                                     it is claimed by another process.
     */
    protected TokenEntry loadOrCreateToken(String processorName, int segment, EntityManager entityManager) {
        TokenEntry token = entityManager
                .find(TokenEntry.class, new TokenEntry.PK(processorName, segment), LockModeType.PESSIMISTIC_WRITE,
                      Collections.singletonMap("javax.persistence.query.timeout", 1));

        if (token == null) {
            token = new TokenEntry(processorName, segment, null, serializer);
            token.claim(nodeId, claimTimeout);
            entityManager.persist(token);
            // hibernate complains about updates in different transactions if this isn't flushed
            entityManager.flush();
        } else if (!token.claim(nodeId, claimTimeout)) {
            throw new UnableToClaimTokenException(
                    format("Unable to claim token '%s[%s]'. It is owned by '%s'", token.getProcessorName(),
                           token.getSegment(), token.getOwner()));
        }
        return token;
    }

    /**
     * Builder class to instantiate a {@link JpaTokenStore}.
     * <p>
     * The {@code claimTimeout} to a 10 seconds duration, and the {@code nodeId} is defaulted to the
     * {@link ManagementFactory#getRuntimeMXBean#getName} output. The {@link EntityManagerProvider} and
     * {@link Serializer} are a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private EntityManagerProvider entityManagerProvider;
        private Serializer serializer;
        private TemporalAmount claimTimeout = Duration.ofSeconds(10);
        private String nodeId = ManagementFactory.getRuntimeMXBean().getName();

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the
         * underlying database.
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
         * Sets the {@code nodeId} to identify ownership of the tokens. Defaults to
         * {@link ManagementFactory#getRuntimeMXBean#getName} output as the node id.
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
