package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

public interface MongoTemplate {

    MongoCollection<Document> trackingTokensCollection();
}
