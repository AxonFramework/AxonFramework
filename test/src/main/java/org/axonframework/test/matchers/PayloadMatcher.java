package org.axonframework.test.matchers;

import org.axonframework.domain.Message;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * Matcher that matches any message (e.g. Event, Command) who's payload matches the given matcher.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class PayloadMatcher<T extends Message> extends BaseMatcher<T> {

    private final Matcher<?> payloadMatcher;

    /**
     * Constructs an instance with the given <code>payloadMatcher</code>.
     *
     * @param payloadMatcher The matcher that must match the Message's payload.
     */
    public PayloadMatcher(Matcher<?> payloadMatcher) {
        this.payloadMatcher = payloadMatcher;
    }

    @Override
    public boolean matches(Object item) {
        return Message.class.isInstance(item)
                && payloadMatcher.matches(((Message) item).getPayload());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Message with payload <");
        payloadMatcher.describeTo(description);
        description.appendText(">");
    }
}
