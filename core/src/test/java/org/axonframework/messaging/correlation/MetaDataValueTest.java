/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.messaging.correlation;

import org.axonframework.messaging.MetaData;
import org.junit.Test;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MetaDataValueTest {

    @Test
    public void createMetaData() {
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");

        assertEquals("value", metaData.get("first"));
        assertFalse(metaData.containsKey("second"));
    }

    @Test
    public void testMergedMetaData() {
        Map<String, Object> metaDataValues = new HashMap<>();
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
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        metaDataValues.put("second", "value");
        MetaData metaData = new MetaData(metaDataValues);

        MetaData newMetaData = metaData.withoutKeys(metaDataValues.keySet());
        assertTrue(newMetaData.isEmpty());
    }

    @Test
    public void testEquals() {
        Map<String, Object> metaDataValues = new HashMap<>();
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

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        MetaData metaData1 = MetaData.from(Collections.singletonMap("Key1", "Value"));
        MetaData metaData2 = MetaData.from(Collections.singletonMap("Key2", "Value"));
        MetaData emptyMetaData = MetaData.emptyInstance();

        assertEquals(metaData1, serialize(metaData1));
        assertEquals(metaData2, serialize(metaData2));
        assertSame(emptyMetaData, serialize(emptyMetaData));
    }

    private MetaData serialize(MetaData metaData1) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(metaData1);
        oos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (MetaData) ois.readObject();
    }

    @Test
    public void testBuildMetaDataThroughFrom() throws Exception {
        Map<String, String> testMetaDataMap = new HashMap<>();
        testMetaDataMap.put("firstKey", "firstVal");
        testMetaDataMap.put("secondKey", "secondVal");

        MetaData result = MetaData.from(testMetaDataMap);

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    public void testBuildMetaDataThroughWith() throws Exception {
        MetaData result = MetaData.with("key", "val");

        assertEquals("val", result.get("key"));
    }

    @Test
    public void testBuildMetaDataThroughWithAnd() throws Exception {
        MetaData result = MetaData.with("firstKey", "firstVal").and("secondKey", "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    public void testBuildMetaDataThroughAndIfNotPresentAddsNewValue() throws Exception {
        MetaData result = MetaData.with("firstKey", "firstVal").andIfNotPresent("secondKey", () -> "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    public void testBuildMetaDataThroughAndIfNotPresentDoesntAddNewValue() throws Exception {
        MetaData result = MetaData.with("firstKey", "firstVal").andIfNotPresent("firstKey", () -> "firstVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals(1, result.size());
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

    @Test
    public void testMetaDataSubsetReturnsSubsetOfMetaDataInstance() throws Exception {
        MetaData testMetaData = MetaData.with("firstKey", "firstValue")
                .and("secondKey", "secondValue")
                .and("thirdKey", "thirdValue")
                .and("fourthKey", "fourthValue");

        MetaData result = testMetaData.subset("secondKey", "fourthKey", "fifthKey");

        assertEquals("secondValue", result.get("secondKey"));
        assertEquals("fourthValue", result.get("fourthKey"));
        assertNull(result.get("fifthKey"));
    }
}
