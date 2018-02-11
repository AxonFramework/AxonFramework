package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.util.Collections.*;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

public class ResultValidatorImplTest {

    private ResultValidator validator;

    @Before
    public void setUp() {
        validator = new ResultValidatorImpl(actualEvents(), new MatchAllFieldFilter(emptyList()));
    }

    private List<EventMessage<?>> actualEvents() {
        return singletonList(asEventMessage(new MyEvent("aggregateId", 123)).andMetaData(singletonMap("key1", "value1")));
    }

    @Test
    public void shouldCompareMetaData() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "otherValue"));

        try {
            validator.expectEvents(expected);
            fail("Expected MetaData differs from actual MetaData");
        } catch (AxonAssertionError error) {
            assertTrue("", error.getMessage().contains("Expected <{key1=otherValue}>"));
            assertTrue("", error.getMessage().contains("got <{key1=value1}>"));
        }
    }
}