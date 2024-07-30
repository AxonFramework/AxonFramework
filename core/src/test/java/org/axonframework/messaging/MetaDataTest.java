/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.messaging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetaDataTest {

    @Test
    public void testAddNullValueToMetaData() throws Exception {
        MetaData metaData = MetaData.with("nullkey", null).and("otherkey", "value").and("lastkey", "lastvalue")
                .subset("nullkey", "otherkey");

        assertEquals(2, metaData.size());
        assertEquals(null, metaData.get("nullkey"));
        assertEquals("value", metaData.get("otherkey"));
    }

    @Test
    public void testToString() {
        assertEquals("MetaData[]", MetaData.emptyInstance().toString());
        assertEquals("MetaData['key'->'value']", MetaData.with("key", "value").toString());
        String actual = MetaData.with("key", "value").and("key2", "value2").toString();
        assertTrue(actual.startsWith("MetaData["));
        assertTrue(actual.contains(", "));
        assertTrue(actual.contains("'key'->'value'"));
        assertTrue(actual.contains("'key2'->'value2'"));
    }
}
