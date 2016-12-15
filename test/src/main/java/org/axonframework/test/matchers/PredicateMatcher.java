package org.axonframework.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.function.Predicate;

/**
 * Matcher implementation that delegates the matching to a Predicate. As predicates do not provide a means to describe '
 * their expectation, this matcher defaults to '[matches a given predicate]'.
 *
 * @param <T> The type of value to match against
 */
public class PredicateMatcher<T> extends TypeSafeMatcher<T> {
    private final Predicate<T> predicate;

    /**
     * Initializes the matcher using given {@code predicate} to test values to match against.
     *
     * @param predicate The predicate defining matching values
     */
    public PredicateMatcher(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    protected boolean matchesSafely(T item) {
        return predicate.test(item);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("[matches a given predicate]");
    }
}
