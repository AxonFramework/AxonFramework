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

import org.axonframework.core.eventhandler.TransactionManager;
import org.axonframework.core.eventhandler.TransactionStatus;

/**
 * A transaction manager that delegates calls to the appropriate annotated methods in a bean. These methods need to be
 * annotated with {@link org.axonframework.core.eventhandler.annotation.BeforeTransaction} and {@link
 * org.axonframework.core.eventhandler.annotation.AfterTransaction} and may have one parameter: {@link
 * org.axonframework.core.eventhandler.TransactionStatus}.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class AnnotationTransactionManager implements TransactionManager {

    private final AnnotationEventHandlerInvoker invoker;

    /**
     * Initialize a transaction manager that delegates calls tot he given <code>bean</code>.
     * @param bean The bean containing annotation transaction management methods
     */
    public AnnotationTransactionManager(Object bean) {
        this.invoker = new AnnotationEventHandlerInvoker(bean);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeTransaction(TransactionStatus transactionStatus) {
        invoker.invokeBeforeTransaction(transactionStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterTransaction(TransactionStatus transactionStatus) {
        invoker.invokeAfterTransaction(transactionStatus);
    }
}
