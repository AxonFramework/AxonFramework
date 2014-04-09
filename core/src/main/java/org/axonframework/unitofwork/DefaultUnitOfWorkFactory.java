/*
 * Copyright (c) 2010-2014. Axon Framework
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

/**
 * {@link UnitOfWorkFactory} implementation that creates instances of the {@link DefaultUnitOfWork}.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class DefaultUnitOfWorkFactory implements UnitOfWorkFactory {

    private final TransactionManager transactionManager;

    /**
     * Initializes the Unit of Work Factory to create Unit of Work that are not bound to any transaction.
     */
    public DefaultUnitOfWorkFactory() {
        this(null);
    }

    /**
     * Initializes the factory to create Unit of Work bound to transactions managed by the given
     * <code>transactionManager</code>
     *
     * @param transactionManager The transaction manager to manage the transactions for Unit Of Work created by this
     *                           factory
     */
    public DefaultUnitOfWorkFactory(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public UnitOfWork createUnitOfWork() {
        return DefaultUnitOfWork.startAndGet(transactionManager);
    }
}
