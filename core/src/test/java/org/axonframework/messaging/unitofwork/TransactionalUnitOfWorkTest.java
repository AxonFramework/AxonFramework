/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.messaging.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.PersistenceException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TransactionalUnitOfWorkTest {

    @Before
    @After
    public void clearCurrentUnitOfWork() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void startAndGet_UnitOfWorkReturned() {
        UnitOfWork<Message<?>> unitOfWork = TransactionalUnitOfWork.startAndGet(null, NoTransactionManager.INSTANCE);

        assertTrue(CurrentUnitOfWork.isStarted());
        assertEquals(CurrentUnitOfWork.get(), unitOfWork);
        assertTrue(CurrentUnitOfWork.get().isActive());
    }

    @Test
    public void startAndGet_UnitOfWorkIsRolledBackOnStartTransactionError() {
        final AtomicReference<UnitOfWork<?>> interceptedUnitOfWork = new AtomicReference<>();

        try {
            TransactionalUnitOfWork.startAndGet(null, () -> {
                interceptedUnitOfWork.set(CurrentUnitOfWork.get());
                throw new PersistenceException("DB Error");
            });
            fail("Exception was not propagated");
        } catch (Exception e) {
            assertEquals(PersistenceException.class, e.getClass());
        }

        assertFalse(CurrentUnitOfWork.isStarted());
        assertTrue(interceptedUnitOfWork.get().isRolledBack());
    }

    @Test
    public void startAndGet_whenNestingUnitsOfWork_andTheTransactionalManagerThrowsAnException_thenTheCurrentUnitOfWorkIsTheOuterUnitOfWork() {
        DefaultUnitOfWork<Message<?>> outerUnitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            TransactionalUnitOfWork.startAndGet(null, () -> {
                throw new PersistenceException("DB Error");
            });
        } catch (Exception e) {
            //Ignore
        }

        assertEquals(outerUnitOfWork, CurrentUnitOfWork.get());
    }
}