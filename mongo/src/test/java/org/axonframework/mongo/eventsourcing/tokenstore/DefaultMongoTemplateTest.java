package org.axonframework.mongo.eventsourcing.tokenstore;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class DefaultMongoTemplateTest {

    private MongoClient mockMongo;
    private MongoDatabase mockDb;
    private MongoCollection<Document> mockCollection;

    @Before
    public void createFixtures() {
        mockMongo = mock(MongoClient.class);
        mockDb = mock(MongoDatabase.class);
        mockCollection = mock(MongoCollection.class);

        when(mockMongo.getDatabase(anyString())).thenReturn(mockDb);
        when(mockDb.getCollection(anyString())).thenReturn(mockCollection);
    }

    @Test
    public void testDefaultValues() throws Exception {
        new DefaultMongoTemplate(mockMongo);

        verify(mockMongo).getDatabase("axonframework");
        verify(mockDb).getCollection("trackingtokens");
    }

    @Test
    public void testCustomValues() throws Exception {
        new DefaultMongoTemplate(mockMongo, "customDatabaseName", "customCollectionName");

        verify(mockMongo).getDatabase("customDatabaseName");
        verify(mockDb).getCollection("customCollectionName");
    }

    @Test
    public void testCreateIndex() throws Exception {
        new DefaultMongoTemplate(mockMongo);

        verify(mockCollection).createIndex(any(Bson.class), any(IndexOptions.class));
    }
}