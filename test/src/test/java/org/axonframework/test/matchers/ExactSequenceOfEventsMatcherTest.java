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

package org.axonframework.test.matchers;

import org.axonframework.eventhandling.EventMessage;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.List;

import static org.axonframework.test.matchers.Matchers.andNoMore;
import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class ExactSequenceOfEventsMatcherTest {

    private Matcher<EventMessage<?>> mockMatcher1;
    private Matcher<EventMessage<?>> mockMatcher2;
    private Matcher<EventMessage<?>> mockMatcher3;
    private Matcher<List<EventMessage<?>>> testSubject;
    private StubEvent stubEvent1;
    private StubEvent stubEvent2;
    private StubEvent stubEvent3;

    @SuppressWarnings({"unchecked"})
    @BeforeEach
    void setUp() {
        mockMatcher1 = mock(Matcher.class);
        mockMatcher2 = mock(Matcher.class);
        mockMatcher3 = mock(Matcher.class);
        testSubject = exactSequenceOf(mockMatcher1, mockMatcher2, mockMatcher3);
        stubEvent1 = new StubEvent();
        stubEvent2 = new StubEvent();
        stubEvent3 = new StubEvent();
        when(mockMatcher1.matches(any())).thenReturn(true);
        when(mockMatcher2.matches(any())).thenReturn(true);
        when(mockMatcher3.matches(any())).thenReturn(true);
    }

    @Test
    void testMatch_FullMatch() {
        assertTrue(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2, stubEvent3)));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher1, never()).matches(stubEvent3);

        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent3);

        verify(mockMatcher3, never()).matches(stubEvent1);
        verify(mockMatcher3, never()).matches(stubEvent2);
        verify(mockMatcher3).matches(stubEvent3);
    }

    @Test
    void testMatch_FullMatchAndNoMore() {
        testSubject = exactSequenceOf(mockMatcher1, mockMatcher2, mockMatcher3, andNoMore());
        assertTrue(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2, stubEvent3)));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher1, never()).matches(stubEvent3);

        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent3);

        verify(mockMatcher3, never()).matches(stubEvent1);
        verify(mockMatcher3, never()).matches(stubEvent2);
        verify(mockMatcher3).matches(stubEvent3);
    }

    @Test
    void testMatch_ExcessIsRefused() {
        testSubject = exactSequenceOf(mockMatcher1, mockMatcher2, mockMatcher3, andNoMore());
        assertFalse(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2, stubEvent3, new StubEvent())));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher1, never()).matches(stubEvent3);

        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent3);

        verify(mockMatcher3, never()).matches(stubEvent1);
        verify(mockMatcher3, never()).matches(stubEvent2);
        verify(mockMatcher3).matches(stubEvent3);
    }

    @Test
    void testMatch_FullMatchWithGaps() {
        reset(mockMatcher2);
        when(mockMatcher2.matches(any())).thenReturn(false);

        assertFalse(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2, stubEvent3)));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher1, never()).matches(stubEvent3);

        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent3);

        verify(mockMatcher3, never()).matches(any());
    }

    @Test
    void testMatch_MoreMatchersThanEvents() {
        when(mockMatcher3.matches(null)).thenReturn(false);
        assertFalse(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2)));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher3, never()).matches(stubEvent1);
        verify(mockMatcher3, never()).matches(stubEvent2);
        verify(mockMatcher3).matches(null);
    }

    @Test
    void testMatch_ExcessEventsIgnored() {
        assertTrue(testSubject.matches(Arrays.asList(stubEvent1, stubEvent2, stubEvent3, new StubEvent())));

        verify(mockMatcher1).matches(stubEvent1);
        verify(mockMatcher1, never()).matches(stubEvent2);
        verify(mockMatcher2, never()).matches(stubEvent1);
        verify(mockMatcher2).matches(stubEvent2);
        verify(mockMatcher3, never()).matches(stubEvent1);
        verify(mockMatcher3, never()).matches(stubEvent2);
        verify(mockMatcher3).matches(stubEvent3);
    }

    @Test
    void testDescribe() {
        testSubject.matches(Arrays.asList(stubEvent1, stubEvent2));

        doAnswer(new DescribingAnswer("A")).when(mockMatcher1).describeTo(isA(Description.class));
        doAnswer(new DescribingAnswer("B")).when(mockMatcher2).describeTo(isA(Description.class));
        doAnswer(new DescribingAnswer("C")).when(mockMatcher3).describeTo(isA(Description.class));
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        String actual = description.toString();
        assertEquals("list with exact sequence of: <A>, <B> and <C>", actual);
    }

    @Test
    void testDescribe_OneMatcherFailed() {
        when(mockMatcher1.matches(any())).thenReturn(true);
        when(mockMatcher2.matches(any())).thenReturn(false);
        when(mockMatcher3.matches(any())).thenReturn(false);

        testSubject.matches(Arrays.asList(stubEvent1, stubEvent2));

        doAnswer(new DescribingAnswer("A")).when(mockMatcher1).describeTo(isA(Description.class));
        doAnswer(new DescribingAnswer("B")).when(mockMatcher2).describeTo(isA(Description.class));
        doAnswer(new DescribingAnswer("C")).when(mockMatcher3).describeTo(isA(Description.class));
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        String actual = description.toString();
        assertEquals("list with exact sequence of: <A>, <B> (FAILED!) and <C>", actual);
    }

    private static class DescribingAnswer implements Answer<Object> {
        private String description;

        DescribingAnswer(String description) {
            this.description = description;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            Description descriptionParameter = (Description) invocation.getArguments()[0];
            descriptionParameter.appendText(this.description);
            return Void.class;
        }
    }
}
