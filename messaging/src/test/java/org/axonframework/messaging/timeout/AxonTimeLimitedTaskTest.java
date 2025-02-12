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
package org.axonframework.messaging.timeout;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class AxonTimeLimitedTaskTest {

    @Test
    void correctlyInterruptsTaskWhenNoWarningWasConfiguredOnUncustomizedConstructor() {
        AxonTimeLimitedTask testSubject = new AxonTimeLimitedTask(
                "My test task",
                100,
                100,
                1
        );


        assertThrows(InterruptedException.class, () -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up. We accept a 50ms delay.
            Thread.sleep(150);
        });

        assertTrue(testSubject.isInterrupted());
        assertFalse(testSubject.isCompleted());
    }


    @Test
    void correctlyInterruptsTaskWithWarningWasConfiguredOnUncustomizedConstructor() {
        AxonTimeLimitedTask testSubject = new AxonTimeLimitedTask(
                "My test task",
                100,
                50,
                10
        );


        assertThrows(InterruptedException.class, () -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up. We accept a 50ms delay.
            Thread.sleep(150);
        });

        assertTrue(testSubject.isInterrupted());
        assertFalse(testSubject.isCompleted());
    }

    @Test
    void correctlyLogsWarningsAndInterruptsWhenWarningWasConfiguredOnCustomizedConstructor() {
        Logger logger = Mockito.spy(LoggerFactory.getLogger("MyLogger"));
        AxonTimeLimitedTask testSubject = new AxonTimeLimitedTask(
                "My test task",
                1000,
                100,
                100,
                AxonTaskJanitor.INSTANCE,
                logger
        );

        assertThrows(InterruptedException.class, () -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up. We accept a 50ms delay.
            Thread.sleep(1500);
        });

        assertTrue(testSubject.isInterrupted());
        assertFalse(testSubject.isCompleted());
        Mockito.verify(logger, Mockito.atLeast(8)).warn(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(),  Mockito.any());
    }


    @Test
    void doesNotInterruptButLogsWarningsIfProcessWasCompletedBeforeTimeout() throws InterruptedException {
        Logger logger = Mockito.spy(LoggerFactory.getLogger("MyLogger"));
        AxonTimeLimitedTask testSubject = new AxonTimeLimitedTask(
                "My test task",
                1000,
                100,
                100,
                AxonTaskJanitor.INSTANCE,
                logger
        );

        testSubject.start();
        // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up. We accept a 50ms delay.
        Thread.sleep(500);

        assertFalse(testSubject.isInterrupted());
        assertFalse(testSubject.isCompleted());
        Mockito.verify(logger, Mockito.atLeast(3)).warn(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(),  Mockito.any());
    }
}