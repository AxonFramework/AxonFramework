/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.matchers;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static org.axonframework.test.matchers.EqualsMatcher.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MapStringEntryMatcherTest {

    private static final Map<String, String> EXPECTED = new HashMap<>();

    static {
        EXPECTED.put("a", "a");
        EXPECTED.put("b", "b");
        EXPECTED.put("c", "c");
    }

    private final MapStringEntryMatcher matcher = new MapStringEntryMatcher(EXPECTED);

    @Test
    void nullSafe() {
        assertFalse(matcher.matches(null));
    }

    @Test
    void expectedEntriesNotPresent() {
        assertFalse(matcher.matches(singletonMap("a", "a")));

        assertThat(matcher.getMissingEntries(), equalTo(newHashMap("b", "b", "c", "c")));
    }

    @Test
    void tooManyEntries() {
        assertFalse(matcher.matches(newHashMap("a", "a", "b", "b", "c", "c", "d", "d", "e", "e")));

        assertThat(matcher.getAdditionalEntries(), equalTo(newHashMap("d", "d", "e", "e")));
    }

    @Test
    void incorrectValue() {
        assertFalse(matcher.matches(newHashMap("a", "a", "b", "b", "c", "CCCC")));

        assertThat(matcher.getAdditionalEntries(), equalTo(newHashMap("c", "CCCC")));

        assertFalse(matcher.matches(newHashMap("a", "a", "b", "b", "c", null)));

        assertThat(matcher.getAdditionalEntries(), equalTo(newHashMap("c", null)));
    }

    @Test
    void incorrectKey() {
        assertFalse(matcher.matches(newHashMap("a", "a", "b", "b", "CCCC", "c")));

        assertThat(matcher.getAdditionalEntries(), equalTo(newHashMap("CCCC", "c")));

        assertFalse(matcher.matches(newHashMap("a", "a", "b", "b", null, "c")));

        assertThat(matcher.getAdditionalEntries(), equalTo(newHashMap(null, "c")));
    }

    @Test
    void anyOrder() {
        TreeMap<String, Object> sortedMap = new TreeMap<>(EXPECTED);

        assertTrue(matcher.matches(sortedMap));
        assertTrue(matcher.matches(sortedMap.descendingMap()));
    }

    @Test
    void matchEmptyMap() {
        assertTrue(new MapStringEntryMatcher(emptyMap()).matches(emptyMap()));
    }

    @Test
    void nullMatchesAsFalseForEmptyMap() {
        assertFalse(new MapStringEntryMatcher(emptyMap()).matches(null));
    }

    @Test
    void nonMapType() {
        assertFalse(new MapStringEntryMatcher(emptyMap()).matches(new Object()));
        assertFalse(new MapStringEntryMatcher(emptyMap()).matches(emptySet()));
    }

    @Test
    void toStringIsAsExpected() {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        assertEquals(
                description.toString(),
                "map containing " + String.format("[<%s=%s>,<%s=%s>,<%s=%s>]",
                                                  EXPECTED.entrySet().stream()
                                                          .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                                                          .toArray()));
    }

    private Map<String, Object> newHashMap(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Must has even number of items");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i = i + 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}