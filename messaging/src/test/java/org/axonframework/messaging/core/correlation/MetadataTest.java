/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.core.correlation;

import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link Metadata}.
 *
 * @author Allard Buijze
 */
class MetadataTest {

    @Test
    void createMetadata() {
        Map<String, String> metadataValues = new HashMap<>();
        metadataValues.put("first", "value");
        Metadata metadata = new Metadata(metadataValues);
        metadataValues.put("second", "value");

        assertEquals("value", metadata.get("first"));
        assertFalse(metadata.containsKey("second"));
    }

    @Test
    void mergedMetadata() {
        Map<String, String> metadataValues = new HashMap<>();
        metadataValues.put("first", "value");
        Metadata metadata = new Metadata(metadataValues);
        metadataValues.put("second", "value");
        metadataValues.put("first", "other");

        Metadata newMetadata = metadata.mergedWith(metadataValues);
        assertEquals("other", newMetadata.get("first"));
        assertEquals("value", newMetadata.get("second"));
    }

    @Test
    void removedMetadata() {
        Map<String, String> metadataValues = new HashMap<>();
        metadataValues.put("first", "value");
        metadataValues.put("second", "value");
        Metadata metadata = new Metadata(metadataValues);

        Metadata newMetadata = metadata.withoutKeys(metadataValues.keySet());
        assertTrue(newMetadata.isEmpty());
    }

    @Test
    void equals() {
        Map<String, String> metadataValues = new HashMap<>();
        metadataValues.put("first", "value");
        Metadata metadata1 = new Metadata(metadataValues);
        metadataValues.put("second", "value");
        Metadata metadata2 = new Metadata(metadataValues);
        Metadata metadata3 = new Metadata(metadataValues);

        assertEquals(metadata1, metadata1);
        assertEquals(metadata2, metadata3);
        assertNotEquals(metadata1, metadata2);
        assertNotEquals(metadata1, metadata3);
        assertNotEquals(metadata3, metadata1);
        assertNotEquals(new Object(), metadata1);
        assertNotEquals(null, metadata1);

        // Map requires that Maps are equal, even if their implementation is different
        assertEquals(metadata2, metadataValues);
        assertEquals(metadataValues, metadata2);
    }

    @Test
    void buildMetadataThroughFrom() {
        Map<String, String> testMetadataMap = new HashMap<>();
        testMetadataMap.put("firstKey", "firstVal");
        testMetadataMap.put("secondKey", "secondVal");

        Metadata result = Metadata.from(testMetadataMap);

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    void buildMetadataThroughWith() {
        Metadata result = Metadata.with("key", "val");

        assertEquals("val", result.get("key"));
    }

    @Test
    void buildMetadataThroughWithAnd() {
        Metadata result = Metadata.with("firstKey", "firstVal").and("secondKey", "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    void buildMetadataThroughAndIfNotPresentAddsNewValue() {
        Metadata result = Metadata.with("firstKey", "firstVal").andIfNotPresent("secondKey", () -> "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    void buildMetadataThroughAndIfNotPresentDoesntAddNewValue() {
        Metadata result = Metadata.with("firstKey", "firstVal").andIfNotPresent("firstKey", () -> "firstVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals(1, result.size());
    }

    @Test
    void metadataModification_Clear() {
        Metadata metadata = new Metadata(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, metadata::clear);
    }

    @Test
    void metadataModification_Put() {
        Metadata metadata = new Metadata(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metadata.put("", ""));
    }

    @Test
    void metadataModification_Remove() {
        Metadata metadata = new Metadata(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metadata.remove(""));
    }

    @Test
    void metadataModification_PutAll() {
        Metadata metadata = new Metadata(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metadata.putAll(Collections.emptyMap()));
    }

    @Test
    void metadataModification_KeySet_Remove() {
        Set<String> keySet = new Metadata(Collections.emptyMap()).keySet();

        assertThrows(UnsupportedOperationException.class, () -> keySet.remove("Hello"));
    }

    @Test
    void metadataModification_Values_Remove() {
        Collection<String> values = new Metadata(Collections.emptyMap()).values();

        assertThrows(UnsupportedOperationException.class, () -> values.remove("Hello"));
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Test
    void metadataModification_EntrySet_Remove() {
        Set<Map.Entry<String, String>> entrySet = new Metadata(Collections.emptyMap()).entrySet();

        assertThrows(UnsupportedOperationException.class, () -> entrySet.remove("Hello"));
    }

    @Test
    void metadataSubsetReturnsSubsetOfMetadataInstance() {
        Metadata testMetadata = Metadata.with("firstKey", "firstValue")
                                        .and("secondKey", "secondValue")
                                        .and("thirdKey", "thirdValue")
                                        .and("fourthKey", "fourthValue");

        Metadata result = testMetadata.subset("secondKey", "fourthKey", "fifthKey");

        assertEquals("secondValue", result.get("secondKey"));
        assertEquals("fourthValue", result.get("fourthKey"));
        assertNull(result.get("fifthKey"));
    }

    @Test
    void addNullValueToMetadata() {
        Metadata metadata = Metadata.with("nullkey", null)
                                    .and("otherkey", "value")
                                    .and("lastkey", "lastvalue")
                                    .subset("nullkey", "otherkey");

        assertEquals(2, metadata.size());
        assertNull(metadata.get("nullkey"));
        assertEquals("value", metadata.get("otherkey"));
    }
}
