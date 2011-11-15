/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.common;

import org.junit.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CollectionUtilsTest {

    @Test
    public void testFilterCollection_FilterOnString() {
        List<Object> items = new ArrayList<Object>();
        items.add("String");
        items.add(new Object());
        items.add(1L);
        List<String> actualResult = CollectionUtils.filterByType(items, String.class);
        assertEquals(1, actualResult.size());
        assertEquals("String", actualResult.get(0));
    }

    @Test
    public void testFilterCollection_FilterOnObject() {
        List<Object> items = new ArrayList<Object>();
        items.add("String");
        items.add(new Object());
        items.add(1L);
        List<Object> actualResult = CollectionUtils.filterByType(items, Object.class);
        assertEquals(3, actualResult.size());
        assertEquals("String", actualResult.get(0));
        assertEquals(new Long(1), actualResult.get(2));
    }

    @Test
    public void testFilterNull() {
        List<String> actualResult = CollectionUtils.filterByType(null, String.class);
        assertEquals(0, actualResult.size());
    }
}
