package org.axonframework.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Matcher testing for exact class
 *
 * @author Yoann CAPLAIN
 * @since 4.6.2
 */
public class ExactClassMatcher<T> extends TypeSafeMatcher<T> {

    private final Class<T> expectedClass;

    public static <T> Matcher<T> exactClassOf(Class<T> expectedClass) {
        return new ExactClassMatcher<T>(expectedClass);
    }

    public ExactClassMatcher(Class<T> expectedClass) {
        assertNonNull(expectedClass, "The expected class should be non-null.");
        this.expectedClass = expectedClass;
    }

    @Override
    protected boolean matchesSafely(T item) {
        return expectedClass.equals(item.getClass());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expectedClass.getName());
        description.appendText(" does not match with the actual type.");
    }
}
