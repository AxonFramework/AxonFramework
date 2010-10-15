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
public class AxonMongoWrapperTest {
    private AxonMongoWrapper wrapperAxon;
    private Mongo mockMongo;
    private DB mockDb;

    @Before
    public void createFixtures() {
        mockMongo = mock(Mongo.class);
        mockDb = mock(DB.class);
        wrapperAxon = new AxonMongoWrapper(mockMongo);
    }


    @Test
    public void testDomainEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        wrapperAxon.domainEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("domainevents");
    }



    @Test
    public void testSnapshotEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        wrapperAxon.snapshotEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("snapshotevents");
    }

    @Test
    public void testDatabase() throws Exception {
        wrapperAxon.database();
        verify(mockMongo).getDB("axonframework");

    }

    @Test
    public void testDomainEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        wrapperAxon.setDomainEventsCollectionName("customdomainevents");
        wrapperAxon.domainEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customdomainevents");
    }



    @Test
    public void testSnapshotEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        wrapperAxon.setSnapshotEventsCollectionName("customsnapshotname");
        wrapperAxon.snapshotEvents();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customsnapshotname");
    }

    @Test
    public void testDatabase_changedName() throws Exception {
        wrapperAxon.setDatabaseName("customdatabase");
        wrapperAxon.database();
        verify(mockMongo).getDB("customdatabase");

    }

}
