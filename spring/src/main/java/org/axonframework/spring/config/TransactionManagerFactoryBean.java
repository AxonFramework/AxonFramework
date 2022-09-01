/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Factory Bean that wraps transaction manager instances with an implementation of {@link TransactionManager}, if
 * necessary (and possible). This allows for transparent and interchangeable use of PlatformTransactionManager and
 * TransactionManager
 *
 * @author Allard Buijze
 * @since 2.0
 * @deprecated Use Spring Boot autoconfiguration or register the individual beans explicitly.
 */
@Deprecated
public class TransactionManagerFactoryBean implements FactoryBean<TransactionManager> {

    private TransactionManager transactionManager;

    @Override
    public TransactionManager getObject() {
        return transactionManager;
    }

    @Override
    public Class<?> getObjectType() {
        return TransactionManager.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Sets the actual transaction manager. If necessary (and possible) it will be wrapped with an implementation of
     * TransactionManager.
     *
     * @param transactionManager The actual transaction manager
     */
    public void setTransactionManager(Object transactionManager) {
        if (transactionManager instanceof TransactionManager) {
            this.transactionManager = (TransactionManager) transactionManager;
        } else if (transactionManager instanceof PlatformTransactionManager) {
            this.transactionManager = new SpringTransactionManager((PlatformTransactionManager) transactionManager);
        } else {
            throw new IllegalArgumentException("Given transaction manager is of unknown type: "
                                                       + transactionManager.getClass().getName());
        }
    }
}
