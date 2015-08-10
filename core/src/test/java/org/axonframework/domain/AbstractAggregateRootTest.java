/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.domain;

import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAggregateRootTest {

    private AggregateRoot testSubject;

    @Before
    public void setUp() {
        testSubject = new AggregateRoot();
    }


    @Test
    public void testRegisterEvent() {
        assertEquals(0, (long) CurrentUnitOfWork.get().getResource("Events/" + testSubject.getIdentifier()));
        testSubject.doSomething();
        assertEquals(1, (long) CurrentUnitOfWork.get().getResource("Events/" + testSubject.getIdentifier()));
    }

    private static class AggregateRoot extends AbstractAggregateRoot {

        private final String identifier;

        private AggregateRoot() {
            identifier = IdentifierFactory.getInstance().generateIdentifier();
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            registerEvent(new StubDomainEvent());
        }
    }
}
