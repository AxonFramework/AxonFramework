package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.junit.*;

import java.util.List;

import static java.util.Collections.*;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

public class ResultValidatorImplTest {

    private ResultValidator<?> validator = new ResultValidatorImpl<>(actualEvents(),
                                                                     new MatchAllFieldFilter(emptyList()),
                                                                     () -> null,
                                                                     null);

    @Test(expected = AxonAssertionError.class)
    public void shouldCompareValuesForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "otherValue"));

        validator.expectEvents(expected);
    }

    @Test(expected = AxonAssertionError.class)
    public void shouldCompareKeysForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("KEY1", "value1"));

        validator.expectEvents(expected);
    }

    @Test
    public void shouldSuccesfullyCompareEqualMetadata() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "value1"));

        validator.expectEvents(expected);
    }

    private List<EventMessage<?>> actualEvents() {
        return singletonList(asEventMessage(new MyEvent("aggregateId", 123))
                .andMetaData(singletonMap("key1", "value1")));
    }

}
