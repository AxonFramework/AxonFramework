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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static org.axonframework.test.matchers.EqualsMatcher.equalTo;

/**
 * Matcher that will match an Object if that object is a {@link Map} of which all keys-values pairs are equal to pairs
 * of the expected instance.
 *
 * @author Benoît Liessens
 * @since 3.3.0
 */
public class MapStringEntryMatcher extends TypeSafeMatcher<Map<String, String>> {

    private final Map<Matcher<String>, Matcher<String>> matchers = new HashMap<>();
    private final Map<String, String> expectedEntries;

    private final Map<String, String> additionalEntries = new HashMap<>();
    private final Map<String, String> missingEntries = new HashMap<>();

    /**
     * Constructs a {@code MapStringEntryMatcher} that expects the given {@code expectedMap} to match.
     *
     * @param expectedMap The map of strings expected to match.
     */
    public MapStringEntryMatcher(Map<String, String> expectedMap) {
        this.expectedEntries = new HashMap<>(expectedMap);
        for (Map.Entry<String, String> entry : expectedMap.entrySet()) {
            this.matchers.put(equalTo(entry.getKey()), equalTo(entry.getValue()));
        }
    }

    @Override
    protected boolean matchesSafely(Map<String, String> actualMap) {
        additionalEntries.clear();
        missingEntries.clear();
        final Matching matching = new Matching(matchers);
        for (Map.Entry<String, String> item : actualMap.entrySet()) {
            if (!matching.matches(item)) {
                additionalEntries.put(item.getKey(), item.getValue());
            }
        }
        if (!matching.isFinished(actualMap)) {
            for (Map.Entry<String, String> item : expectedEntries.entrySet()) {
                if (matching.matches(item)) {
                    missingEntries.put(item.getKey(), item.getValue());
                }
            }
            return false;
        }
        return additionalEntries.isEmpty();
    }

    /**
     * Returns The additional entries that were present in the {@code expectedMap} after matching.
     *
     * @return The additional entries that were present in the {@code expectedMap} after matching.
     */
    public Map<String, String> getAdditionalEntries() {
        return unmodifiableMap(additionalEntries);
    }

    /**
     * Returns The missing entries that were present in the {@code expectedMap} after matching.
     *
     * @return The missing entries that were present in the {@code expectedMap} after matching.
     */
    public Map<String, String> getMissingEntries() {
        return unmodifiableMap(missingEntries);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("map containing ").appendValueList("[", ",", "]", expectedEntries.entrySet());
    }

    private record Matching(Map<Matcher<String>, Matcher<String>> matchers) {

        private Matching(Map<Matcher<String>, Matcher<String>> matchers) {
            this.matchers = new HashMap<>(matchers);
        }

        public boolean matches(Map.Entry<String, String> item) {
            if (matchers.isEmpty()) {
                return false;
            }
            for (Map.Entry<Matcher<String>, Matcher<String>> matcherEntry : matchers.entrySet()) {
                if (matcherEntry.getKey().matches(item.getKey()) && matcherEntry.getValue().matches(item.getValue())) {
                    matchers.remove(matcherEntry.getKey());
                    return true;
                }
            }
            return false;
        }

        public boolean isFinished(Map<String, String> items) {
            return matchers.isEmpty();
        }
    }
}
