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

package org.axonframework.test.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StubAggregateLifecycleTest {
    private StubAggregateLifecycle testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new StubAggregateLifecycle();
    }

    @AfterEach
    void tearDown() {
        testSubject.close();
    }

    @Test
    void lifecycleIsNotRegisteredAutomatically() {
        assertThrows(IllegalStateException.class, () -> apply("test"));
    }

    @Test
    void applyingEventsAfterDeactivationFails() {
        testSubject.activate();
        testSubject.close();

        assertThrows(IllegalStateException.class, () -> apply("test"));
    }

    @Test
    void appliedEventsArePassedToActiveLifecycle() {
        testSubject.activate();
        apply("test");

        assertEquals(1, testSubject.getAppliedEvents().size());
        assertEquals("test", testSubject.getAppliedEventPayloads().get(0));
        assertEquals("test", testSubject.getAppliedEvents().get(0).getPayload());
    }

    @Test
    void markDeletedIsRegisteredWithActiveLifecycle() {
        testSubject.activate();
        markDeleted();

        assertEquals(0, testSubject.getAppliedEvents().size());
        assertTrue(testSubject.isMarkedDeleted());
    }
}
