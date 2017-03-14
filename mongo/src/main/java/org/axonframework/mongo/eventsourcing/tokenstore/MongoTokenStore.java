package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.UpdateResult;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.eventhandling.tokenstore.GenericTokenEntry;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
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

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static java.lang.String.format;

/**
 * An implementation of TokenStore that allows you store and retrieve tracking tokens with MongoDB.
 */
public class MongoTokenStore implements TokenStore {

    private final MongoTemplate mongoTemplate;
    private final Serializer serializer;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final Class<?> contentType;

    private final static Clock clock = Clock.systemUTC();
    private final static Logger logger = LoggerFactory.getLogger(MongoTokenStore.class);

    /**
     * Creates a MongoTokenStore with a default claim timeout of 10 seconds, a default owner identifier and a default
     * content type.
     *
     * @param mongoTemplate used to access the collection in which tracking tokens are stored
     * @param serializer    serializer used to serialize tracking tokens
     */
    public MongoTokenStore(MongoTemplate mongoTemplate, Serializer serializer) {
        this(mongoTemplate,
             serializer,
             Duration.ofSeconds(10),
             ManagementFactory.getRuntimeMXBean().getName(),
             byte[].class);
    }

    /**
     * Creates a MongoTokenStore by using the given values.
     *
     * @param mongoTemplate used to access the collection in which tracking tokens are stored
     * @param serializer    serializer used to serialize TrackingToken
     * @param claimTimeout  the amount of time after which a claim is automatically released
     * @param nodeId        the owner identifier that this token store uses
     * @param contentType   the data type of the serialized tracking token
     */
    public MongoTokenStore(MongoTemplate mongoTemplate, Serializer serializer, TemporalAmount claimTimeout,
                           String nodeId, Class<?> contentType) {
        this.mongoTemplate = mongoTemplate;
        this.serializer = serializer;
        this.claimTimeout = claimTimeout;
        this.nodeId = nodeId;
        this.contentType = contentType;
    }

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) throws UnableToClaimTokenException {
        updateOrInsertTokenEntry(token, processorName, segment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TrackingToken fetchToken(String processorName, int segment) throws UnableToClaimTokenException {
        AbstractTokenEntry<?> tokenEntry = loadOrInsertTokenEntry(processorName, segment);
        return tokenEntry.getToken(serializer);
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

    private <T> T readSerializedData(Document document) {
        if (byte[].class.equals(contentType)) {
            Binary token = document.get("token", Binary.class);
            return (T) ((token != null) ? token.getData() : null);
        }
        return (T) document.get("token", contentType);
    }
}
