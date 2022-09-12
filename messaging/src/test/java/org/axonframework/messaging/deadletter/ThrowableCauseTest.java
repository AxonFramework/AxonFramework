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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ThrowableCause}.
 *
 * @author Steven van Beelen
 */
class ThrowableCauseTest {

    @Test
    void constructThrowableCauseWithThrowable() {
        Throwable testThrowable = new RuntimeException("some-message");

        ThrowableCause testSubject = new ThrowableCause(testThrowable);

        assertEquals(testThrowable.getClass().getName(), testSubject.type());
        assertEquals(testThrowable.getMessage(), testSubject.message());
    }

    @Test
    void constructThrowableCauseWithTypeAndMessage() {
        String testType = "type";
        String testMessage = "message";

        ThrowableCause testSubject = new ThrowableCause(testType, testMessage);

        assertEquals(testType, testSubject.type());
        assertEquals(testMessage, testSubject.message());
    }
}