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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the common functionality of the {@link DeadLetterEntry}.
 *
 * @author Steven van Beelen
 */
class DeadLetterEntryTest {

    @Test
    void testCompareReturnsValueLowerThanZeroIfFirstLetterOccurredBeforeSecond() {
        Instant firstLetterTimestamp = Instant.ofEpochMilli(3000);
        Instant secondLetterTimestamp = Instant.ofEpochMilli(6000);
        TestDeadLetterEntry firstLetter = new TestDeadLetterEntry(firstLetterTimestamp);
        TestDeadLetterEntry secondLetter = new TestDeadLetterEntry(secondLetterTimestamp);

        assertTrue(DeadLetterEntry.compare(firstLetter, secondLetter) < 0);
    }

    @Test
    void testCompareReturnsValueHigherThanZeroIfFirstLetterOccurredAfterSecond() {
        Instant firstLetterTimestamp = Instant.ofEpochMilli(6000);
        Instant secondLetterTimestamp = Instant.ofEpochMilli(3000);
        TestDeadLetterEntry firstLetter = new TestDeadLetterEntry(firstLetterTimestamp);
        TestDeadLetterEntry secondLetter = new TestDeadLetterEntry(secondLetterTimestamp);

        assertTrue(DeadLetterEntry.compare(firstLetter, secondLetter) > 0);
    }

    @Test
    void testCompareReturnsZeroIfFirstAndSecondLetterOccurredAtTheSameTime() {
        Instant firstLetterTimestamp = Instant.ofEpochMilli(3000);
        Instant secondLetterTimestamp = Instant.ofEpochMilli(3000);
        TestDeadLetterEntry firstLetter = new TestDeadLetterEntry(firstLetterTimestamp);
        TestDeadLetterEntry secondLetter = new TestDeadLetterEntry(secondLetterTimestamp);

        assertEquals(0, DeadLetterEntry.compare(firstLetter, secondLetter));
    }

    private static class TestDeadLetterEntry implements DeadLetterEntry<Message<?>> {

        private final Instant deadLettered;

        private TestDeadLetterEntry(Instant deadLettered) {
            this.deadLettered = deadLettered;
        }

        @Override
        public QueueIdentifier queueIdentifier() {
            return StaticQueueIdentifier.INSTANCE;
        }

        @Override
        public Message<?> message() {
            return null;
        }

        @Override
        public Throwable cause() {
            return null;
        }

        @Override
        public Instant expiresAt() {
            return null;
        }

        @Override
        public Instant deadLettered() {
            return deadLettered;
        }

        @Override
        public void acknowledge() {
            // Implementation not needed
        }

        @Override
        public void evict() {
            // Implementation not needed
        }

        private static class StaticQueueIdentifier implements QueueIdentifier {

            private static final StaticQueueIdentifier INSTANCE = new StaticQueueIdentifier();

            @Override
            public Object identifier() {
                return "some-identifier";
            }

            @Override
            public String group() {
                return "some-group";
            }
        }
    }
}