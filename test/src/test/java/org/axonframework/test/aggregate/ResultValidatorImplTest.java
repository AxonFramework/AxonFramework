package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.junit.Test;

import java.util.List;

import static java.util.Collections.*;
import static junit.framework.TestCase.fail;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

public class ResultValidatorImplTest {

    private ResultValidator validator = new ResultValidatorImpl(actualEvents(), new MatchAllFieldFilter(emptyList()));

    @Test
    public void shouldCompareValuesForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "otherValue"));

        validate(expected);

    }

    private List<EventMessage<?>> actualEvents() {
        return singletonList(asEventMessage(new MyEvent("aggregateId", 123)).andMetaData(singletonMap("key1", "value1")));
    }

    private void validate(EventMessage<?> expected) {
        try {
            validator.expectEvents(expected);
            fail("Expected MetaData differs from actual MetaData");
        } catch (AxonAssertionError error) {
        }
    }

    @Test
    public void shouldCompareKeysForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("KEY1", "value1"));

        validate(expected);
    }
}