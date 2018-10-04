/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.UpdateResult;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.eventhandling.tokenstore.GenericTokenEntry;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.jpa.TokenEntry;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.serialization.Serializer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * An implementation of TokenStore that allows you store and retrieve tracking tokens with MongoDB.
 *
 * @author Joris van der Kallen
 * @since 3.1
 */
public class MongoTokenStore implements TokenStore {

    private final static Logger logger = LoggerFactory.getLogger(MongoTokenStore.class);
    private final static Clock clock = Clock.systemUTC();

    private final MongoTemplate mongoTemplate;
    private final Serializer serializer;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final Class<?> contentType;

    /**
     * Instantiate a {@link MongoTokenStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoTemplate} and {@link Serializer} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MongoTokenStore} instance
     */
    protected MongoTokenStore(Builder builder) {
        builder.validate();
        this.mongoTemplate = builder.mongoTemplate;
        this.serializer = builder.serializer;
        this.claimTimeout = builder.claimTimeout;
        this.nodeId = builder.nodeId;
        this.contentType = builder.contentType;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MongoTokenStore}.
     * <p>
     * The {@code claimTimeout} is defaulted to a 10 seconds duration (by using {@link Duration#ofSeconds(long)},
     * {@code nodeId} is defaulted to the {@link ManagementFactory#getRuntimeMXBean#getName} output and the
     * {@code contentType} to a {@code byte[]} {@link Class}. The {@link MongoTemplate} and {@link Serializer} are
     * <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link MongoTokenStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) throws UnableToClaimTokenException {
        updateOrInsertTokenEntry(token, processorName, segment);
    }

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount) throws UnableToClaimTokenException {
        initializeTokenSegments(processorName, segmentCount, null);
    }

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount, TrackingToken initialToken)
            throws UnableToClaimTokenException {
        if (fetchSegments(processorName).length > 0) {
            throw new UnableToClaimTokenException(
                    "Unable to initialize segments. Some tokens were already present for the given processor."
            );
        }

        List<Document> entries = IntStream.range(0, segmentCount)
                                          .mapToObj(segment -> new GenericTokenEntry<>(initialToken, serializer,
                                                                                       contentType, processorName,
                                                                                       segment))
                                          .map(this::tokenEntryToDocument)
                                          .collect(Collectors.toList());
        mongoTemplate.trackingTokensCollection()
                     .insertMany(entries, new InsertManyOptions().ordered(false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TrackingToken fetchToken(String processorName, int segment) throws UnableToClaimTokenException {
        AbstractTokenEntry<?> tokenEntry = loadOrInsertTokenEntry(processorName, segment);
        return tokenEntry.getToken(serializer);
    }

    @Override
    public void extendClaim(String processorName, int segment) throws UnableToClaimTokenException {
        UpdateResult updateResult =
                mongoTemplate.trackingTokensCollection()
                             .updateOne(and(eq("processorName", processorName),
                                            eq("segment", segment),
                                            eq("owner", nodeId)),
                                        set("timestamp", TokenEntry.clock.instant().toEpochMilli()));
        if (updateResult.getMatchedCount() == 0) {
            throw new UnableToClaimTokenException(format(
                    "Unable to extend claim on token token '%s[%s]'. It is owned by another segment.",
                    processorName, segment
            ));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseClaim(String processorName, int segment) {
        UpdateResult updateResult = mongoTemplate.trackingTokensCollection()
                                                 .updateOne(and(
                                                         eq("processorName", processorName),
                                                         eq("segment", segment),
                                                         eq("owner", nodeId)
                                                 ), set("owner", null));

        if (updateResult.getMatchedCount() == 0) {
            logger.warn("Releasing claim of token {}/{} failed. It was owned by another node.", processorName, segment);
        }
    }

    @Override
    public int[] fetchSegments(String processorName) {
        ArrayList<Integer> segments = mongoTemplate.trackingTokensCollection()
                                                   .find(eq("processorName", processorName))
                                                   .sort(ascending("segment"))
                                                   .projection(fields(include("segment"), excludeId()))
                                                   .map(d -> d.get("segment", Integer.class))
                                                   .into(new ArrayList<>());
        // toArray doesn't work because of autoboxing limitations
        int[] ints = new int[segments.size()];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = segments.get(i);
        }
        return ints;
    }

    /**
     * Creates a filter that allows you to retrieve a claimable token entry with a given processor name and segment.
     *
     * @param processorName the processor name of the token entry
     * @param segment       the segment of the token entry
     * @return the filter
     */
    private Bson claimableTokenEntryFilter(String processorName, int segment) {
        return and(
                eq("processorName", processorName),
                eq("segment", segment),
                or(eq("owner", nodeId),
                   eq("owner", null),
                   lt("timestamp", clock.instant().minus(claimTimeout).toEpochMilli()))
        );
    }

    private void updateOrInsertTokenEntry(TrackingToken token, String processorName, int segment) {
        AbstractTokenEntry<?> tokenEntry = new GenericTokenEntry<>(token,
                                                                   serializer,
                                                                   contentType,
                                                                   processorName,
                                                                   segment);
        tokenEntry.claim(nodeId, claimTimeout);
        Bson update = combine(set("owner", nodeId),
                              set("timestamp", tokenEntry.timestamp().toEpochMilli()),
                              set("token", tokenEntry.getSerializedToken().getData()),
                              set("tokenType", tokenEntry.getSerializedToken().getType().getName()));
        UpdateResult updateResult = mongoTemplate.trackingTokensCollection()
                                                 .updateOne(claimableTokenEntryFilter(processorName, segment), update);

        if (updateResult.getModifiedCount() == 0) {
            try {
                mongoTemplate.trackingTokensCollection()
                             .insertOne(tokenEntryToDocument(tokenEntry));
            } catch (MongoWriteException exception) {
                if (ErrorCategory.fromErrorCode(exception.getError().getCode()) == ErrorCategory.DUPLICATE_KEY) {
                    throw new UnableToClaimTokenException(format("Unable to claim token '%s[%s]'",
                                                                 processorName,
                                                                 segment));
                }
            }
        }
    }

    private AbstractTokenEntry<?> loadOrInsertTokenEntry(String processorName, int segment) {
        Document document = mongoTemplate.trackingTokensCollection()
                                         .findOneAndUpdate(claimableTokenEntryFilter(processorName, segment),
                                                           combine(set("owner", nodeId),
                                                                   set("timestamp", clock.millis())),
                                                           new FindOneAndUpdateOptions()
                                                                   .returnDocument(ReturnDocument.AFTER));

        if (document == null) {
            try {
                AbstractTokenEntry<?> tokenEntry = new GenericTokenEntry<>(null,
                                                                           serializer,
                                                                           contentType,
                                                                           processorName,
                                                                           segment);
                tokenEntry.claim(nodeId, claimTimeout);

                mongoTemplate.trackingTokensCollection()
                             .insertOne(tokenEntryToDocument(tokenEntry));

                return tokenEntry;
            } catch (MongoWriteException exception) {
                if (ErrorCategory.fromErrorCode(exception.getError().getCode()) == ErrorCategory.DUPLICATE_KEY) {
                    throw new UnableToClaimTokenException(format("Unable to claim token '%s[%s]'",
                                                                 processorName,
                                                                 segment));
                }
            }
        }
        return documentToTokenEntry(document);
    }

    private Document tokenEntryToDocument(AbstractTokenEntry<?> tokenEntry) {
        return new Document("processorName", tokenEntry.getProcessorName())
                .append("segment", tokenEntry.getSegment())
                .append("owner", tokenEntry.getOwner())
                .append("timestamp", tokenEntry.timestamp().toEpochMilli())
                .append("token",
                        tokenEntry.getSerializedToken() == null ? null : tokenEntry.getSerializedToken().getData())
                .append("tokenType",
                        tokenEntry.getSerializedToken() == null ? null : tokenEntry.getSerializedToken()
                                                                                   .getContentType().getName());
    }

    private AbstractTokenEntry<?> documentToTokenEntry(Document document) {
        return new GenericTokenEntry<>(
                readSerializedData(document),
                document.getString("tokenType"),
                Instant.ofEpochMilli(document.getLong("timestamp")).toString(),
                document.getString("owner"),
                document.getString("processorName"),
                document.getInteger("segment"),
                contentType);
    }

    @SuppressWarnings("unchecked")
    private <T> T readSerializedData(Document document) {
        if (byte[].class.equals(contentType)) {
            Binary token = document.get("token", Binary.class);
            return (T) ((token != null) ? token.getData() : null);
        }
        return (T) document.get("token", contentType);
    }

    /**
     * Creates the indexes required to work with the TokenStore.
     */
    @PostConstruct
    public void ensureIndexes() {
        mongoTemplate.trackingTokensCollection().createIndex(Indexes.ascending("processorName", "segment"),
                                                             new IndexOptions().unique(true));
    }

    /**
     * Builder class to instantiate a {@link MongoTokenStore}.
     * <p>
     * The {@code claimTimeout} is defaulted to a 10 seconds duration (by using {@link Duration#ofSeconds(long)},
     * {@code nodeId} is defaulted to the {@link ManagementFactory#getRuntimeMXBean#getName} output and the
     * {@code contentType} to a {@code byte[]} {@link Class}. The {@link MongoTemplate} and {@link Serializer} are
     * <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private MongoTemplate mongoTemplate;
        private Serializer serializer;
        private TemporalAmount claimTimeout = Duration.ofSeconds(10);
        private String nodeId = ManagementFactory.getRuntimeMXBean().getName();
        private Class<?> contentType = byte[].class;

        /**
         * Sets the {@link MongoTemplate} providing access to the collection which stores the {@link TrackingToken}s.
         *
         * @param mongoTemplate the {@link MongoTemplate} providing access to the collection which stores the
         *                      {@link TrackingToken}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoTemplate(MongoTemplate mongoTemplate) {
            assertNonNull(mongoTemplate, "MongoTemplate may not be null");
            this.mongoTemplate = mongoTemplate;
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
         * Sets the {@code contentType} to which a {@link TrackingToken} should be serialized. Defaults to a
         * {@code byte[]} {@link Class} type.
         *
         * @param contentType the content type as a {@link Class} to which a {@link TrackingToken} should be serialized
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder contentType(Class<?> contentType) {
            assertNonNull(contentType, "The content type may not be null");
            this.contentType = contentType;
            return this;
        }

        /**
         * Initializes a {@link MongoTokenStore} as specified through this Builder.
         *
         * @return a {@link MongoTokenStore} as specified through this Builder
         */
        public MongoTokenStore build() {
            return new MongoTokenStore(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(mongoTemplate, "The MongoTemplate is a hard requirement and should be provided");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
        }

        private void assertNodeId(String nodeId, String exceptionMessage) {
            assertThat(nodeId, name -> Objects.nonNull(name) && !"".equals(name), exceptionMessage);
        }
    }
}
