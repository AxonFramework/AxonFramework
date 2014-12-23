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

package org.axonframework.repository;

import org.axonframework.domain.AbstractAggregateRoot;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;

/**
 * @author Allard Buijze
 */
public class AbstractRepositoryTest {

    private AbstractRepository testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AbstractRepository<JpaAggregate>(JpaAggregate.class) {
            @Override
            protected void doSave(JpaAggregate aggregate) {
            }

            @Override
            protected JpaAggregate doLoad(String aggregateIdentifier, Long expectedVersion) {
                return new JpaAggregate();
            }

            @Override
            protected void doDelete(JpaAggregate aggregate) {
            }
        };
        DefaultUnitOfWork.startAndGet();
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregateTypeVerification_CorrectType() throws Exception {
        testSubject.add(new JpaAggregate("hi"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateTypeVerification_WrongType() throws Exception {
        testSubject.add(new AbstractAggregateRoot() {
            @Override
            public String getIdentifier() {
                return "1";
            }
        });
    }
}
