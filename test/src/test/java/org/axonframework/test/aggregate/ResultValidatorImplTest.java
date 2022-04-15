/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.junit.jupiter.api.*;

import java.util.List;

import static java.util.Collections.*;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ResultValidatorImpl}.
 *
 * @author bliessens
 */
class ResultValidatorImplTest {

    private ResultValidator<?> validator = new ResultValidatorImpl<>(actualEvents(),
                                                                     new MatchAllFieldFilter(emptyList()),
                                                                     () -> null,
                                                                     null);

    @Test
    void shouldCompareValuesForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "otherValue"));

        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(expected));
    }

    @Test
    void shouldCompareKeysForEquality() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("KEY1", "value1"));

        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(expected));
    }

    @Test
    void shouldSuccessfullyCompareEqualMetadata() {
        EventMessage<?> expected = actualEvents().iterator().next().andMetaData(singletonMap("key1", "value1"));

        validator.expectEvents(expected);
    }

    @Test
    void shouldConsiderExplicitEqualsBeforeCheckingFields() {
        String s1 = "0";
        validator = new ResultValidatorImpl<>(singletonList(asEventMessage(s1)),
                                              new MatchAllFieldFilter(emptyList()),
                                              () -> null,
                                              null);
        String s2 = String.valueOf(0);
        assertEquals(s1, s2);

        //noinspection unused -> the hash code is cached in a String
        int ignored = s1.hashCode();

        validator.expectEvents(s2);
    }

    @Test
    void shouldReportFailureForFailedPrimitiveMatching() {
        validator = new ResultValidatorImpl<>(singletonList(asEventMessage("some-string")),
                                              new MatchAllFieldFilter(emptyList()),
                                              () -> null,
                                              null);

        assertThrows(AxonAssertionError.class, () -> validator.expectEvents("some-other-string"));
    }

    @Test
    void shouldReportFailureForFailedFieldMatching() {
        validator = new ResultValidatorImpl<>(singletonList(asEventMessage(new MyEvent("some-string", 1))),
                                              new MatchAllFieldFilter(emptyList()),
                                              () -> null,
                                              null);

        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(new MyEvent("some-other-string", 1)));
    }

    private List<EventMessage<?>> actualEvents() {
        return singletonList(asEventMessage(new MyEvent("aggregateId", 123))
                                     .andMetaData(singletonMap("key1", "value1")));
    }
}
