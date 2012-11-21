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

package org.axonframework.unitofwork;

import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CurrentUnitOfWorkTest {

    @Before
    public void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSession_NoCurrentSession() {
        CurrentUnitOfWork.get();
    }

    @Test
    public void testSetSession() {
        UnitOfWork mockUnitOfWork = mock(UnitOfWork.class);
        CurrentUnitOfWork.set(mockUnitOfWork);
        assertSame(mockUnitOfWork, CurrentUnitOfWork.get());

        CurrentUnitOfWork.clear(mockUnitOfWork);
        assertFalse(CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testNotCurrentUnitOfWorkCommitted() {
        DefaultUnitOfWork outerUoW = new DefaultUnitOfWork();
        outerUoW.start();
        new DefaultUnitOfWork().start();
        try {
            outerUoW.commit();
        } catch (IllegalStateException e) {
            assertTrue("Wrong type of message: " + e.getMessage(), e.getMessage().contains("not the active"));
        }
    }

}
