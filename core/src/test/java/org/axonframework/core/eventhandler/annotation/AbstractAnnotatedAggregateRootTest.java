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

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.DomainEvent;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;

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
        assertEquals(0, testSubject.getUncommittedEventCount());

        testSubject.handle(new StubDomainEvent());

        assertEquals(1, testSubject.invocationCount);

        try {
            testSubject.handle(new DomainEvent() {
            });
            fail("Expected UnsupportedOperationException. There is no event handler for this aggregate.");
        }
        catch (UnsupportedOperationException e) {
            // this is what we wanted
        }

        assertEquals(1, testSubject.invocationCount);
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;

        private SimpleAggregateRoot() {
            super();
        }

        @EventHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
        }
    }
}
