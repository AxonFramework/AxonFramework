/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class DefaultClusterMetaDataTest {

    private DefaultClusterMetaData config;

    @Before
    public void setUp() throws Exception {
        config = new DefaultClusterMetaData();
    }

    @Test
    public void testStoreNullValue() {
        config.setProperty("null", null);

        assertNull(config.getProperty("null"));
    }

    @Test
    public void testStoreAndDelete() {
        assertFalse(config.isPropertySet("key"));

        config.setProperty("key", "value");
        assertTrue(config.isPropertySet("key"));

        config.setProperty("key", null);
        assertTrue(config.isPropertySet("key"));

        config.removeProperty("key");
        assertFalse(config.isPropertySet("key"));
    }

    @Test
    public void testStoreAndGet() {
        assertNull(config.getProperty("key"));
        Object value = new Object();
        config.setProperty("key", value);
        assertSame(value, config.getProperty("key"));
    }

    @Test
    public void testOverwriteProperties() {
        assertNull(config.getProperty("key"));
        Object value1 = new Object();
        config.setProperty("key", value1);
        assertSame(value1, config.getProperty("key"));

        Object value2 = new Object();
        config.setProperty("key", value2);
        assertSame(value2, config.getProperty("key"));
    }
}
