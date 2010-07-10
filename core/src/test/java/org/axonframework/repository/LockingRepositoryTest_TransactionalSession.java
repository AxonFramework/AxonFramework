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

package org.axonframework.repository;

import org.axonframework.commandhandling.interceptors.TransactionalUnitOfWork;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;

/**
 * @author Allard Buijze
 */
public class LockingRepositoryTest_TransactionalSession extends LockingRepositoryTest {

    @Before
    public void startSession() {
        CurrentUnitOfWork.set(new TransactionalUnitOfWork());
    }

    @After
    public void tearDown() {
        CurrentUnitOfWork.clear();
    }

}
