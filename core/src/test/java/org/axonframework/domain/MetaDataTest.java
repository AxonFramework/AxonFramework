package org.axonframework.domain;

import org.junit.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MetaDataTest {

    @Test
    public void createMetaData() {
        Map<String, Object> metaDataValues = new HashMap<String, Object>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");

        assertEquals("value", metaData.get("first"));
        assertFalse(metaData.containsKey("second"));
    }

    @Test
    public void testMergedMetaData() {
        Map<String, Object> metaDataValues = new HashMap<String, Object>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        metaDataValues.put("first", "other");

        MetaData newMetaData = metaData.mergedWith(metaDataValues);
        assertEquals("other", newMetaData.get("first"));
        assertEquals("value", newMetaData.get("second"));
    }

    @Test
    public void testRemovedMetaData() {
        Map<String, Object> metaDataValues = new HashMap<String, Object>();
        metaDataValues.put("first", "value");
        metaDataValues.put("second", "value");
        MetaData metaData = new MetaData(metaDataValues);

        MetaData newMetaData = metaData.withoutKeys(metaDataValues.keySet());
        assertTrue(newMetaData.isEmpty());
    }

    @Test
    public void testEquals() {
        Map<String, Object> metaDataValues = new HashMap<String, Object>();
        metaDataValues.put("first", "value");
        MetaData metaData1 = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        MetaData metaData2 = new MetaData(metaDataValues);
        MetaData metaData3 = new MetaData(metaDataValues);

        assertEquals(metaData1, metaData1);
        assertEquals(metaData2, metaData3);
        assertFalse(metaData1.equals(metaData2));
        assertFalse(metaData1.equals(metaData3));
        assertFalse(metaData3.equals(metaData1));
        assertFalse(metaData1.equals(new Object()));
        assertFalse(metaData1.equals(null));

        // Map requires that Maps are equal, even if their implementation is different
        assertEquals(metaData2, metaDataValues);
        assertEquals(metaDataValues, metaData2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_Clear() {
        new MetaData(Collections.<String, Object>emptyMap()).clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_Put() {
        new MetaData(Collections.<String, Object>emptyMap()).put("", "");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_Remove() {
        new MetaData(Collections.<String, Object>emptyMap()).remove("");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_PutAll() {
        new MetaData(Collections.<String, Object>emptyMap()).putAll(Collections.<String, Object>emptyMap());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_KeySet_Remove() {
        new MetaData(Collections.<String, Object>emptyMap()).keySet().remove("Hello");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_Values_Remove() {
        new MetaData(Collections.<String, Object>emptyMap()).values().remove("Hello");
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Test(expected = UnsupportedOperationException.class)
    public void testMetaDataModification_EntrySet_Remove() {
        new MetaData(Collections.<String, Object>emptyMap()).entrySet().remove("Hello");
    }
}
