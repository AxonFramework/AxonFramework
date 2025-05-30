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

package org.axonframework.messaging.correlation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MetaData}.
 *
 * @author Allard Buijze
 */
class MetaDataTest {

    private Converter converter;

    @BeforeEach
    void setUp() {
        converter = spy(new ToStringConverter());
    }

    @Test
    void createMetaData() {
        Map<String, String> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");

        assertEquals("value", metaData.get("first"));
        assertFalse(metaData.containsKey("second"));
    }

    @Test
    void mergedMetaData() {
        Map<String, String> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        metaDataValues.put("first", "other");

        MetaData newMetaData = metaData.mergedWith(metaDataValues);
        assertEquals("other", newMetaData.get("first"));
        assertEquals("value", newMetaData.get("second"));
    }

    @Test
    void removedMetaData() {
        Map<String, String> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        metaDataValues.put("second", "value");
        MetaData metaData = new MetaData(metaDataValues);

        MetaData newMetaData = metaData.withoutKeys(metaDataValues.keySet());
        assertTrue(newMetaData.isEmpty());
    }

    @Test
    void equals() {
        Map<String, String> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData1 = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        MetaData metaData2 = new MetaData(metaDataValues);
        MetaData metaData3 = new MetaData(metaDataValues);

        assertEquals(metaData1, metaData1);
        assertEquals(metaData2, metaData3);
        assertNotEquals(metaData1, metaData2);
        assertNotEquals(metaData1, metaData3);
        assertNotEquals(metaData3, metaData1);
        assertNotEquals(new Object(), metaData1);
        assertNotEquals(null, metaData1);

        // Map requires that Maps are equal, even if their implementation is different
        assertEquals(metaData2, metaDataValues);
        assertEquals(metaDataValues, metaData2);
    }

    @Test
    void buildMetaDataThroughFrom() {
        Map<String, String> testMetaDataMap = new HashMap<>();
        testMetaDataMap.put("firstKey", "firstVal");
        testMetaDataMap.put("secondKey", "secondVal");
        testMetaDataMap.put("thirdKey", null);

        MetaData result = MetaData.from(testMetaDataMap);

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
        assertNull(result.get("thirdKey"));
    }

    @Test
    void buildMetaDataThroughWith() {
        MetaData result = MetaData.with("key", "val");

        assertEquals("val", result.get("key"));
    }

    @Test
    void buildMetaDataThroughWithAllowsNulls() {
        MetaData result = MetaData.with("key", null);

        assertNull(result.get("key"));
    }

    @Test
    void buildMetaDataThroughWithConverter() {
        MetaDataValue testValue = new MetaDataValue("text", 42L);
        String expectedValue = converter.convert(testValue, String.class);

        MetaData result = MetaData.with("key", testValue, converter);

        assertEquals(expectedValue, result.get("key"));
    }

    @Test
    void buildMetaDataThroughWithAnd() {
        MetaData result = MetaData.with("firstKey", "firstVal")
                                  .and("secondKey", "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughWithAndAllowsNulls() {
        MetaData result = MetaData.with("firstKey", null)
                                  .and("secondKey", null);

        assertNull(result.get("firstKey"));
        assertNull(result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughWithAndConverter() {
        MetaDataValue testValueOne = new MetaDataValue("val1", 1L);
        MetaDataValue testValueTwo = new MetaDataValue("val2", 2L);
        String expectedValueOne = converter.convert(testValueOne, String.class);
        String expectedValueTwo = converter.convert(testValueTwo, String.class);

        MetaData result = MetaData.with("firstKey", testValueOne, converter)
                                  .and("secondKey", testValueTwo, converter);

        assertEquals(expectedValueOne, result.get("firstKey"));
        assertEquals(expectedValueTwo, result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentAddsNewValue() {
        MetaData result = MetaData.with("firstKey", "firstVal")
                                  .andIfNotPresent("secondKey", () -> "secondVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals("secondVal", result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentAddsNewNullValue() {
        MetaData result = MetaData.with("firstKey", "firstVal")
                                  .andIfNotPresent("secondKey", () -> null);

        assertEquals("firstVal", result.get("firstKey"));
        assertNull(result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentAddsNewValueWithConverter() {
        MetaDataValue testValue = new MetaDataValue("val1", 1L);
        String expectedValue = converter.convert(testValue, String.class);

        MetaData result = MetaData.with("firstKey", "firstVal")
                                  .andIfNotPresent("secondKey", () -> testValue, converter);

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals(expectedValue, result.get("secondKey"));
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentDoesntAddNewValue() {
        MetaData result = MetaData.with("firstKey", "firstVal").andIfNotPresent("firstKey", () -> "firstVal");

        assertEquals("firstVal", result.get("firstKey"));
        assertEquals(1, result.size());
    }

    @Test
    void getWithConverterDoesNotConvertForNullValue() {
        MetaData testSubject = MetaData.emptyInstance();

        assertNull(testSubject.get("some-non-existing-key", MetaDataValue.class, converter));
        verifyNoInteractions(converter);
    }

    @Test
    void getWithConverterConvertsTheUncoveredValue() {
        Converter mockedConverter = mock(Converter.class);
        String testKey = "key";
        MetaDataValue testValue = new MetaDataValue("val1", 1L);
        when(mockedConverter.convert(testValue, String.class)).thenReturn(testValue.toString());
        when(mockedConverter.convert(testValue.toString(), MetaDataValue.class)).thenReturn(testValue);

        MetaData testSubject = MetaData.with(testKey, testValue, mockedConverter);

        MetaDataValue result = testSubject.get(testKey, MetaDataValue.class, mockedConverter);
        assertEquals(testValue, result);
    }

    @Test
    void getOrDefaultWithConverterDoesNotConvertForNullValue() {
        MetaDataValue defaultValue = new MetaDataValue("defaultVal", Long.MAX_VALUE);

        MetaData testSubject = MetaData.emptyInstance();

        MetaDataValue result = testSubject.getOrDefault("some-non-existing-key",
                                                        MetaDataValue.class,
                                                        converter,
                                                        defaultValue);

        assertEquals(defaultValue, result);
        verifyNoInteractions(converter);
    }

    @Test
    void getOrDefaultWithConverterConvertsTheUncoveredValue() {
        Converter mockedConverter = mock(Converter.class);
        String testKey = "key";
        MetaDataValue testValue = new MetaDataValue("val1", 1L);
        MetaDataValue defaultValue = new MetaDataValue("defaultVal", Long.MAX_VALUE);
        when(mockedConverter.convert(testValue, String.class)).thenReturn(testValue.toString());
        when(mockedConverter.convert(testValue.toString(), MetaDataValue.class)).thenReturn(testValue);

        MetaData testSubject = MetaData.with(testKey, testValue, mockedConverter);

        MetaDataValue result = testSubject.getOrDefault(testKey, MetaDataValue.class, mockedConverter, defaultValue);
        assertEquals(testValue, result);
        assertNotEquals(defaultValue, result);
    }

    @Test
    void metaDataModification_Clear() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, metaData::clear);
    }

    @Test
    void metaDataModification_Put() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metaData.put("", ""));
    }

    @Test
    void metaDataModification_Remove() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metaData.remove(""));
    }

    @Test
    void metaDataModification_PutAll() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> metaData.putAll(Collections.emptyMap()));
    }

    @Test
    void metaDataModification_KeySet_Remove() {
        Set<String> keySet = new MetaData(Collections.emptyMap()).keySet();

        assertThrows(UnsupportedOperationException.class, () -> keySet.remove("Hello"));
    }

    @Test
    void metaDataModification_Values_Remove() {
        Collection<String> values = new MetaData(Collections.emptyMap()).values();

        assertThrows(UnsupportedOperationException.class, () -> values.remove("Hello"));
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Test
    void metaDataModification_EntrySet_Remove() {
        Set<Map.Entry<String, String>> entrySet = new MetaData(Collections.emptyMap()).entrySet();

        assertThrows(UnsupportedOperationException.class, () -> entrySet.remove("Hello"));
    }

    @Test
    void metaDataSubsetReturnsSubsetOfMetaDataInstance() {
        MetaData testMetaData = MetaData.with("firstKey", "firstValue")
                                        .and("secondKey", "secondValue")
                                        .and("thirdKey", "thirdValue")
                                        .and("fourthKey", "fourthValue");

        MetaData result = testMetaData.subset("secondKey", "fourthKey", "fifthKey");

        assertEquals("secondValue", result.get("secondKey"));
        assertEquals("fourthValue", result.get("fourthKey"));
        assertNull(result.get("fifthKey"));
    }

    @Test
    void addNullValueToMetaData() {
        MetaData metaData = MetaData.with("nullkey", null)
                                    .and("otherkey", "value")
                                    .and("lastkey", "lastvalue")
                                    .subset("nullkey", "otherkey");

        assertEquals(2, metaData.size());
        assertNull(metaData.get("nullkey"));
        assertEquals("value", metaData.get("otherkey"));
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private record MetaDataValue(String text, Long value) {

    }
}
