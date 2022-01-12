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

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.deadline.ScheduledDeadlineInfo;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.*;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultValidatorImplTest {

    @Mock
    private StubDeadlineManager deadlineManager;
    private ResultValidator<?> validator;

    private final Instant deadlineWindowFrom = Instant.now();
    private final Instant deadlineWindowTo = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setup() {
        validator = new ResultValidatorImpl<>(actualEvents(),
                                              AllFieldsFilter.instance(),
                                              () -> null,
                                              deadlineManager);
    }

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
    void shouldSuccesfullyCompareEqualMetadata() {
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

        // the hash code is cached in a String
        int ignored = s1.hashCode();

        validator.expectEvents(s2);
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineAtFrom() {
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(deadlineWindowFrom)));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlineAtTo() {
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(deadlineWindowTo)));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineMatchingInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom, deadlineWindowTo, Matchers.anything()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class,
                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline.deadlineMessage().getPayload()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class,
                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline.deadlineMessage().getPayload()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class,
                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline.deadlineMessage().getPayload()));
    }

    @Test
    void noDeadlineInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadlineBefore.deadlineMessage().getPayload()));
        assertDoesNotThrow(() -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadlineAfter.deadlineMessage().getPayload()));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineOfTypeInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom, deadlineWindowTo, String.class));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineInsideWindow() {
        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineAtFrom() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlineAtTo() {
        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));

        assertThrows(AxonAssertionError.class, () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    @Test
    void noDeadlineWithNameInTimeframeWithDeadlinesOutsideWindow() {
        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));

        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom, deadlineWindowTo, "deadlineName"));
    }

    private List<EventMessage<?>> actualEvents() {
        return singletonList(asEventMessage(new MyEvent("aggregateId", 123))
                                     .andMetaData(singletonMap("key1", "value1")));
    }

    private ScheduledDeadlineInfo createDeadline(Instant expiryTime) {
        DeadlineMessage<String> deadlineMessage = GenericDeadlineMessage.asDeadlineMessage("deadlineName", "payload", expiryTime);
        return new ScheduledDeadlineInfo(expiryTime, "deadlineName", "1", 0, deadlineMessage, null);
    }
}
