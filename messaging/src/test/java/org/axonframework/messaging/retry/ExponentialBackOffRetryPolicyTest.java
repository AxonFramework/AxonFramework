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

package org.axonframework.messaging.retry;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackOffRetryPolicyTest {

    private final Message<?> message = new GenericMessage<>(new MessageType("message"), "test");
    private final MockException failure = new MockException("Simulating failure");

    private ExponentialBackOffRetryPolicy testSubject;

    @BeforeEach
    void setup() {
        testSubject = new ExponentialBackOffRetryPolicy(10);
    }

    @Test
    void scheduleRetryAtIndicatedIntervalForFirstRetry() {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, List.of());

        assertEquals(10, actual.rescheduleInterval());
        assertEquals(TimeUnit.MILLISECONDS, actual.rescheduleIntervalTimeUnit());
        assertTrue(actual.shouldReschedule());
    }

    @Test
    void scheduleRetryAtDoubleIntervalForFirstRetry() {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, failureCount(1));

        assertEquals(20, actual.rescheduleInterval());
        assertEquals(TimeUnit.MILLISECONDS, actual.rescheduleIntervalTimeUnit());
        assertTrue(actual.shouldReschedule());
    }

    @Test
    void scheduleRetryAtQuadrupleIntervalForSecondRetry() {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, failureCount(2));

        assertEquals(40, actual.rescheduleInterval());
        assertEquals(TimeUnit.MILLISECONDS, actual.rescheduleIntervalTimeUnit());
        assertTrue(actual.shouldReschedule());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5, 15, 59, 60, 62, 63, 64, 100})
    void scheduleRetryCapsAtLongMaxValue(int count) {
        RetryPolicy.Outcome actual = testSubject.defineFor(message, failure, failureCount(count));

        assertTrue(actual.rescheduleInterval() >= 10);
        assertEquals(TimeUnit.MILLISECONDS, actual.rescheduleIntervalTimeUnit());
        assertTrue(actual.shouldReschedule());
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