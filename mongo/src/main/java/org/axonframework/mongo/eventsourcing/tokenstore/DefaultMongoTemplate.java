package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.axonframework.mongo.AbstractMongoTemplate;
import org.bson.Document;

public class DefaultMongoTemplate extends AbstractMongoTemplate implements MongoTemplate {

    private static final String DEFAULT_TRACKINGTOKENS_COLLECTION_NAME = "trackingtokens";

    private final String trackingTokensCollectionName;

    /**
     * Initialize a template for the given {@code mongoDb} instance, using default database name ("axonframework")
     * and collection name ("trackingtokens").
     *
     * @param mongo The Mongo instance providing access to the database
     */
    public DefaultMongoTemplate(MongoClient mongo) {
        super(mongo);
        this.trackingTokensCollectionName = DEFAULT_TRACKINGTOKENS_COLLECTION_NAME;
        createIndex();
    }

    /**
     * Creates a template connecting to given {@code mongo} instance, and loads tracking tokens in the collection with
     * given {@code trackingTokensCollectionName}, in a database with given {@code databaseName}.
     *
     * @param mongo                        The Mongo instance configured to connect to the Mongo Server
     * @param databaseName                 The name of the database containing the data
     * @param trackingTokensCollectionName The collection containing the tracking tokens
     */
    public DefaultMongoTemplate(MongoClient mongo, String databaseName, String trackingTokensCollectionName) {
        super(mongo, databaseName);
        this.trackingTokensCollectionName = trackingTokensCollectionName;
        createIndex();
    }

    @Override
    public MongoCollection<Document> trackingTokensCollection() {
        return database().getCollection(trackingTokensCollectionName);
    }

    private void createIndex() {
        trackingTokensCollection().createIndex(Indexes.ascending("processorName", "segment"),
                                               new IndexOptions().unique(true));
    }
}
