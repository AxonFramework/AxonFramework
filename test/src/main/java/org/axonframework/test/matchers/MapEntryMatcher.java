package org.axonframework.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static org.axonframework.test.matchers.EqualsMatcher.equalTo;

/**
 * Matcher that will match an Object if that object is a {@link Map} of which all
 * keys-values pairs are equal to pairs of the expected instance.
 *
 * @author bliessens
 * @since 3.3
 */
public class MapEntryMatcher extends TypeSafeMatcher<Map<String, Object>> {

    private final Map<Matcher<String>, Matcher<Object>> matchers = new HashMap<>();
    private final Map<String, Object> expectedEntries;

    private final Map<String, Object> additionalEntries = new HashMap<>();
    private final Map<String, Object> missingEntries = new HashMap<>();

    public MapEntryMatcher(Map<String, Object> expectedMap) {
        this.expectedEntries = new HashMap<>(expectedMap);
        for (Map.Entry<String, Object> entry : expectedMap.entrySet()) {
            this.matchers.put(equalTo(entry.getKey()), equalTo(entry.getValue()));
        }
    }

    @Override
    protected boolean matchesSafely(Map<String, Object> actualMap) {
        additionalEntries.clear();
        missingEntries.clear();
        final Matching<Map.Entry<String, Object>> matching = new Matching(matchers);
        for (Map.Entry<String, Object> item : actualMap.entrySet()) {
            if (!matching.matches(item)) {
                additionalEntries.put(item.getKey(), item.getValue());
            }
        }
        if (!matching.isFinished(actualMap)) {
            for (Map.Entry<String, Object> item : expectedEntries.entrySet()) {
                if (matching.matches(item)) {
                    missingEntries.put(item.getKey(), item.getValue());
                }
            }
            return false;
        }
        return additionalEntries.isEmpty();
    }

    public Map<String, Object> getAdditionalEntries() {
        return unmodifiableMap(additionalEntries);
    }

    public Map<String, Object> getMissingEntries() {
        return unmodifiableMap(missingEntries);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("map containing ").appendValueList("[", ",", "]", expectedEntries.entrySet());
    }

    private static class Matching<S> {
        private final Map<Matcher<String>, Matcher<Object>> matchers;

        public Matching(Map<Matcher<String>, Matcher<Object>> matchers) {
            this.matchers = new HashMap(matchers);
        }

        public boolean matches(Map.Entry<String, Object> item) {
            if (matchers.isEmpty()) {
                return false;
            }
            for (Map.Entry<Matcher<String>, Matcher<Object>> matcherEntry : matchers.entrySet()) {

                if (matcherEntry.getKey().matches(item.getKey()) && matcherEntry.getValue().matches(item.getValue())) {
                    matchers.remove(matcherEntry.getKey());
                    return true;
                }
            }
            return false;
        }

        public boolean isFinished(Map<String, Object> items) {
            return matchers.isEmpty();
        }

    }
}
