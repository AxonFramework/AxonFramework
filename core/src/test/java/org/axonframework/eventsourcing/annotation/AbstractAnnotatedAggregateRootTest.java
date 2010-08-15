/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        assertEquals(1, testSubject.getUncommittedEventCount());

        testSubject.handle(new StubDomainEvent());

        assertEquals(2, testSubject.invocationCount);
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;

        private SimpleAggregateRoot() {
            apply(new StubDomainEvent());
        }

        public SimpleAggregateRoot(UUID uuid) {
            super(uuid);
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
        }
    }
}
