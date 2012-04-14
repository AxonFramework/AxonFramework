package org.axonframework.test.matchers;

import org.axonframework.domain.Message;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches a list of Messages if the list of their payloads matches the given matcher..
 *
 * @param <T> The type of Message the matcher can match against
 * @author Allard Buijze
 * @since 2.0
 */
public class PayloadsMatcher<T extends Message> extends BaseMatcher<List<? extends T>> {
    private final Matcher<? extends List> matcher;
    private final Class<? extends T> expectedMessageType;

    /**
     * Constructs an instance that uses the given <code>matcher</code> to match the payloads.
     *
     * @param expectedMessageType The type of message this matcher will accept
     * @param matcher             The matcher to match the payloads with
     */
    public PayloadsMatcher(Class<? extends T> expectedMessageType, Matcher<? extends List> matcher) {
        this.matcher = matcher;
        this.expectedMessageType = expectedMessageType;
    }

    @Override
    public boolean matches(Object item) {
        if (!List.class.isInstance(item)) {
            return false;
        }
        List<Object> payloads = new ArrayList<Object>();
        for (Object listItem : (List) item) {
            if (Message.class.isInstance(listItem)) {
                if (!expectedMessageType.isInstance(listItem)) {
                    return false;
                }
                payloads.add(((Message) listItem).getPayload());
            } else {
                payloads.add(item);
            }
        }
        return matcher.matches(payloads);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("List with EventMessages with Payloads matching <");
        matcher.describeTo(description);
        description.appendText(">");
    }
}
