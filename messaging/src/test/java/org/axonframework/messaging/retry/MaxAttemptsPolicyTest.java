/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.retry;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaxAttemptsPolicyTest {

    private final Message<?> message = new GenericMessage<>(new QualifiedName("test", "message", "0.0.1"), "test");
    private final MockException failure = new MockException("Simulating failure");
    private MaxAttemptsPolicy testSubject;
    private RetryPolicy mock;

    @BeforeEach
    void setUp() {
        mock = mock();
        testSubject = new MaxAttemptsPolicy(mock, 10);
        when(mock.defineFor(any(), any(), any())).thenReturn(RetryPolicy.Outcome.rescheduleIn(1, TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5, 9})
    void shouldInvokeDelegateWhenMaxAttemptsAreNotReached(int count) {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, failureCount(count));

        verify(mock).defineFor(eq(message), eq(failure), anyList());

        assertEquals(1, actual.rescheduleInterval());
        assertEquals(TimeUnit.SECONDS, actual.rescheduleIntervalTimeUnit());
        assertTrue(actual.shouldReschedule());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 100})
    void shouldBlockRetriesWhenMaximumIsReachedOrExceeded(int count) {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, failureCount(count));

        verify(mock, never()).defineFor(any(), any(), anyList());

        assertFalse(actual.shouldReschedule());
    }

    @Test
    void shouldDescribeProperties() {
        ComponentDescriptor descriptor = mock();
        testSubject.describeTo(descriptor);

        verify(descriptor).describeWrapperOf(mock);
        verify(descriptor).describeProperty("maxAttempts", 10);
    }

    private List<Class<? extends Throwable>[]> failureCount(int count) {
        List<Class<? extends Throwable>[]> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            //noinspection unchecked
            list.add(new Class[]{Throwable.class});
        }
        return list;
    }
}