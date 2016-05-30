/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.serialization;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SerializedMetaDataTest {

    @Test
    public void testSerializeMetaData() {
        byte[] stubData = new byte[]{};
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(stubData, byte[].class);
        assertEquals(stubData, serializedMetaData.getData());
        assertEquals(byte[].class, serializedMetaData.getContentType());
        assertNull(serializedMetaData.getType().getRevision());
        assertEquals("org.axonframework.messaging.metadata.MetaData", serializedMetaData.getType().getName());
    }

    @Test
    public void testIsSerializedMetaData() {
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(new byte[]{}, byte[].class);
        assertTrue(SerializedMetaData.isSerializedMetaData(serializedMetaData));
        assertFalse(SerializedMetaData.isSerializedMetaData(
                new SimpleSerializedObject<>("test", String.class, "type", "rev")));
    }

}
