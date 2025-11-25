/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test class validating the {@link ResultValidatorImpl}.
 *
 * @author bliessens
 */
@ExtendWith(MockitoExtension.class)
class ResultValidatorImplTest {

//    @Mock
//    private StubDeadlineManager deadlineManager;
//    private ResultValidator<?> validator;
//
//    private final Instant deadlineWindowFrom = Instant.now();
//    private final Instant deadlineWindowTo = Instant.now().plus(2, ChronoUnit.DAYS);
//
//    @BeforeEach
//    void setup() {
//        validator = new ResultValidatorImpl<>(actualEvents(),
//                                              AllFieldsFilter.instance(),
//                                              () -> null,
//                                              deadlineManager);
//    }
//
//    @Test
//    void shouldCompareValuesForEquality() {
//        EventMessage expected = actualEvents().iterator().next().andMetadata(singletonMap("key1", "otherValue"));
//
//        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(expected));
//    }
//
//    @Test
//    void shouldCompareKeysForEquality() {
//        EventMessage expected = actualEvents().iterator().next().andMetadata(singletonMap("KEY1", "value1"));
//
//        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(expected));
//    }
//
//    @Test
//    void shouldSuccessfullyCompareEqualMetadata() {
//        EventMessage expected = actualEvents().iterator().next().andMetadata(singletonMap("key1", "value1"));
//
//        validator.expectEvents(expected);
//    }
//
//    @Test
//    void shouldConsiderExplicitEqualsBeforeCheckingFields() {
//        String s1 = "0";
//        validator = new ResultValidatorImpl<>(singletonList(asEventMessage(s1)),
//                                              new MatchAllFieldFilter(emptyList()),
//                                              () -> null,
//                                              null);
//        String s2 = String.valueOf(0);
//        assertEquals(s1, s2);
//
//        //noinspection unused -> the hash code is cached in a String
//        int ignored = s1.hashCode();
//
//        validator.expectEvents(s2);
//    }
//
//    @Test
//    void shouldReportFailureForFailedPrimitiveMatching() {
//        validator = new ResultValidatorImpl<>(singletonList(asEventMessage("some-string")),
//                                              new MatchAllFieldFilter(emptyList()),
//                                              () -> null,
//                                              null);
//
//        assertThrows(AxonAssertionError.class, () -> validator.expectEvents("some-other-string"));
//    }
//
//    @Test
//    void shouldReportFailureForFailedFieldMatching() {
//        validator = new ResultValidatorImpl<>(singletonList(asEventMessage(new MyEvent("some-string", 1))),
//                                              new MatchAllFieldFilter(emptyList()),
//                                              () -> null,
//                                              null);
//
//        assertThrows(AxonAssertionError.class, () -> validator.expectEvents(new MyEvent("some-other-string", 1)));
//    }
//
//    @Test
//    void noDeadlineMatchingInTimeframeWithDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       Matchers.anything()));
//    }
//
//    @Test
//    void noDeadlineMatchingInTimeframeWithOtherDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom,
//                                                                             deadlineWindowTo,
//                                                                             Matchers.nullValue()));
//    }
//
//    @Test
//    void noDeadlineMatchingInTimeframeWithDeadlineAtFrom() {
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(
//                deadlineWindowFrom)));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       Matchers.anything()));
//    }
//
//    @Test
//    void noDeadlineMatchingInTimeframeWithDeadlineAtTo() {
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(
//                deadlineWindowTo)));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       Matchers.anything()));
//    }
//
//    @Test
//    void noDeadlineMatchingInTimeframeWithDeadlinesOutsideWindow() {
//        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
//        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineMatching(deadlineWindowFrom,
//                                                                             deadlineWindowTo,
//                                                                             Matchers.anything()));
//    }
//
//    @Test
//    void noDeadlineInTimeframeWithDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        ScheduledDeadlineInfo deadlineInfo = createDeadline(expiryTime);
//        Object deadline = deadlineInfo.deadlineMessage().payload();
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
//    }
//
//    @Test
//    void noDeadlineInTimeframeWithOtherDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadline(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     new Object()));
//    }
//
//    @Test
//    void noDeadlineInTimeframeWithDeadlineAtFrom() {
//        ScheduledDeadlineInfo deadlineInfo = createDeadline(deadlineWindowFrom);
//        Object deadline = deadlineInfo.deadlineMessage().payload();
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
//    }
//
//    @Test
//    void noDeadlineInTimeframeWithDeadlineAtTo() {
//        ScheduledDeadlineInfo deadlineInfo = createDeadline(deadlineWindowTo);
//        Object deadline = deadlineInfo.deadlineMessage().payload();
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadlineInfo));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadline(deadlineWindowFrom, deadlineWindowTo, deadline));
//    }
//
//    @Test
//    void noDeadlineInTimeframeWithDeadlinesOutsideWindow() {
//        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
//        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadline(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     deadlineBefore.deadlineMessage().payload()));
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadline(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     deadlineAfter.deadlineMessage().payload()));
//    }
//
//    @Test
//    void noDeadlineOfTypeInTimeframeWithDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        ScheduledDeadlineInfo deadline = createDeadline(expiryTime);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     String.class));
//    }
//
//    @Test
//    void noDeadlineOfTypeInTimeframeWithOtherDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom,
//                                                                           deadlineWindowTo,
//                                                                           Integer.class));
//    }
//
//    @Test
//    void noDeadlineOfTypeInTimeframeWithDeadlineAtFrom() {
//        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     String.class));
//    }
//
//    @Test
//    void noDeadlineOfTypeInTimeframeWithDeadlineAtTo() {
//        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom,
//                                                                     deadlineWindowTo,
//                                                                     String.class));
//    }
//
//    @Test
//    void noDeadlineOfTypeInTimeframeWithDeadlinesOutsideWindow() {
//        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
//        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineOfType(deadlineWindowFrom,
//                                                                           deadlineWindowTo,
//                                                                           String.class));
//    }
//
//    @Test
//    void noDeadlineWithNameInTimeframeWithDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       "deadlineName"));
//    }
//
//    @Test
//    void noDeadlineWithNameTimeframeWithOtherDeadlineInsideWindow() {
//        Instant expiryTime = deadlineWindowFrom.plus(1, ChronoUnit.DAYS);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(createDeadline(expiryTime)));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom,
//                                                                             deadlineWindowTo,
//                                                                             "otherName"));
//    }
//
//    @Test
//    void noDeadlineWithNameInTimeframeWithDeadlineAtFrom() {
//        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowFrom);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       "deadlineName"));
//    }
//
//    @Test
//    void noDeadlineWithNameInTimeframeWithDeadlineAtTo() {
//        ScheduledDeadlineInfo deadline = createDeadline(deadlineWindowTo);
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Collections.singletonList(deadline));
//
//        assertThrows(AxonAssertionError.class,
//                     () -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom,
//                                                                       deadlineWindowTo,
//                                                                       "deadlineName"));
//    }
//
//    @Test
//    void noDeadlineWithNameInTimeframeWithDeadlinesOutsideWindow() {
//        ScheduledDeadlineInfo deadlineBefore = createDeadline(deadlineWindowFrom.minus(1, ChronoUnit.DAYS));
//        ScheduledDeadlineInfo deadlineAfter = createDeadline(deadlineWindowTo.plus(1, ChronoUnit.DAYS));
//        when(deadlineManager.getScheduledDeadlines()).thenReturn(Arrays.asList(deadlineBefore, deadlineAfter));
//
//        assertDoesNotThrow(() -> validator.expectNoScheduledDeadlineWithName(deadlineWindowFrom,
//                                                                             deadlineWindowTo,
//                                                                             "deadlineName"));
//    }
//
//    private List<EventMessage> actualEvents() {
//        return singletonList(asEventMessage(new MyEvent("aggregateId", 123))
//                                     .andMetadata(singletonMap("key1", "value1")));
//    }
//
//    private ScheduledDeadlineInfo createDeadline(Instant expiryTime) {
//        var payload = "payload";
//        DeadlineMessage deadlineMessage = new GenericDeadlineMessage(
//                "deadlineName", new GenericMessage(new MessageType(payload.getClass()), payload), () -> expiryTime
//        );
//        return new ScheduledDeadlineInfo(expiryTime, "deadlineName", "1", 0, deadlineMessage, null);
//    }
//
//    @Test
//    void hamcrestMatcherMismatchIsReported() {
//        // this matcher implementation will always fail and return expected strings
//        final DiagnosingMatcher<List<? super EventMessage>> matcher = new DiagnosingMatcher<List<? super EventMessage>>() {
//            @Override
//            protected boolean matches(Object item, Description mismatchDescription) {
//                mismatchDescription.appendText("<MISMATCH TEXT>");
//                return false;
//            }
//
//            @Override
//            public void describeTo(Description description) {
//                description.appendText("<EXPECTED DESCRIPTION TEXT>");
//            }
//        };
//        try {
//            validator.expectEventsMatching(matcher);
//            fail("expected expectEventsMatching to throw AxonAssertionError");
//        } catch (AxonAssertionError e) {
//            assertThat(e.getMessage(), containsString("<EXPECTED DESCRIPTION TEXT>"));
//            assertThat(e.getMessage(), containsString("<MISMATCH TEXT>"));
//        }
//    }
//
//    private static EventMessage asEventMessage(Object payload) {
//        return new GenericEventMessage(new MessageType("event"), payload);
//    }
}
