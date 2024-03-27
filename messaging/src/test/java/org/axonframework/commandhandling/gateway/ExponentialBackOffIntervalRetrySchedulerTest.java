/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.retry.ExponentialBackOffRetryPolicy;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackOffIntervalRetrySchedulerTest {

    private ExponentialBackOffRetryPolicy testSubject;

    @BeforeEach
    void setup() {
        testSubject = new ExponentialBackOffRetryPolicy(10);
    }

    @Test
    void scheduleRetry() {
        fail("Not implemented yet");
    }
}