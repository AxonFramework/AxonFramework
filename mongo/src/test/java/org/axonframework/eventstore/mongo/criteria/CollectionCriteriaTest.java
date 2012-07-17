package org.axonframework.eventstore.mongo.criteria;

import org.junit.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CollectionCriteriaTest {

    @Test
    public void testValueInCollection() {
        List<String> collection = new ArrayList<String>();
        collection.add("first");
        collection.add("second");
        MongoCriteria actual = new MongoProperty("prop").in(collection);

        assertEquals("{ \"prop\" : { \"$in\" : [ \"first\" , \"second\"]}}", actual.asMongoObject().toString());
    }

    @Test
    public void testValueInString() {
        String collection = "collection";
        MongoCriteria actual = new MongoProperty("prop").in(collection);

        assertEquals("{ \"prop\" : { \"$in\" : \"collection\"}}", actual.asMongoObject().toString());
    }

    @Test
    public void testValueInStringArray() {
        String[] collection = {"first", "second"};
        MongoCriteria actual = new MongoProperty("prop").in(collection);

        assertEquals("{ \"prop\" : { \"$in\" : [ \"first\" , \"second\"]}}", actual.asMongoObject().toString());
    }

    @Test
    public void testValueNotInCollection() {
        List<String> collection = new ArrayList<String>();
        collection.add("first");
        collection.add("second");
        MongoCriteria actual = new MongoProperty("prop").notIn(collection);

        assertEquals("{ \"prop\" : { \"$nin\" : [ \"first\" , \"second\"]}}", actual.asMongoObject().toString());
    }

    @Test
    public void testValueNotInString() {
        String collection = "collection";
        MongoCriteria actual = new MongoProperty("prop").notIn(collection);

        assertEquals("{ \"prop\" : { \"$nin\" : \"collection\"}}", actual.asMongoObject().toString());
    }

    @Test
    public void testValueNotInStringArray() {
        String[] collection = {"first", "second"};
        MongoCriteria actual = new MongoProperty("prop").notIn(collection);

        assertEquals("{ \"prop\" : { \"$nin\" : [ \"first\" , \"second\"]}}", actual.asMongoObject().toString());
    }
}
