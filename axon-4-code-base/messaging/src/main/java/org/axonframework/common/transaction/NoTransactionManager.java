/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.transaction;

/**
 * TransactionManager implementation that does nothing. It's a placeholder implementation for the cases where no
 * special transaction management is required.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum NoTransactionManager implements TransactionManager {

    /**
     * Singleton instance of the TransactionManager
     */
    INSTANCE;

    /**
     * Returns the singleton instance of this TransactionManager
     *
     * @return the singleton instance of this TransactionManager
     */
    public static TransactionManager instance() {
        return INSTANCE;
    }

    @Override
    public Transaction startTransaction() {
        return TRANSACTION;
    }

    private static final Transaction TRANSACTION = new Transaction() {
        @Override
        public void commit() {
            //no op
        }

        @Override
        public void rollback() {
            //no op
        }
    };
}
