package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.Mongo;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jettro Coenradie
 */
public class MongoHelperTest {
    private MongoHelper helper;
    private Mongo mockMongo;
    private DB mockDb;

    @Before
    public void createFixtures() {
        mockMongo = mock(Mongo.class);
        mockDb = mock(DB.class);
        helper = new MongoHelper(mockMongo);
    }


    @Test
    public void testDomainEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        helper.domainEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("domainevents");
    }



    @Test
    public void testSnapshotEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        helper.snapshotEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("snapshotevents");
    }

    @Test
    public void testDatabase() throws Exception {
        helper.database();
        verify(mockMongo).getDB("axonframework");

    }

    @Test
    public void testDomainEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        helper.setDomainEventsCollectionName("customdomainevents");
        helper.domainEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customdomainevents");
    }



    @Test
    public void testSnapshotEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        helper.setSnapshotEventsCollectionName("customsnapshotname");
        helper.snapshotEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customsnapshotname");
    }

    @Test
    public void testDatabase_changedName() throws Exception {
        helper.setDatabaseName("customdatabase");
        helper.database();
        verify(mockMongo).getDB("customdatabase");

    }

}
